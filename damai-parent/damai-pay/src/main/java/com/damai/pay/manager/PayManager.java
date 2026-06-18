package com.damai.pay.manager;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constants.RedisKeyConstant;
import com.damai.pay.dao.PayMapper;
import com.damai.pay.entity.Pay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 支付单 Manager（DB 读写 + 缓存 + 幂等键胶水层）。
 *
 * <p>职责（约束 §6.2：缓存/中间件操作集中在 Manager，Service 不直接操作 Redis）：
 * <ul>
 *   <li>支付单 CRUD（ShardingSphere 分片路由自动命中同分片组）</li>
 *   <li>支付单查询缓存（短 TTL，防穿透）</li>
 *   <li>支付回调幂等键操作（Redis GETDEL 原子消费，回调幂等的第一道防线）</li>
 *   <li>对账任务查询「待支付且超时未回调」的支付单</li>
 * </ul>
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayManager {

    private final PayMapper payMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 支付单缓存前缀（按 orderId 维度缓存，对账/查询高频用）
     */
    private static final String PAY_CACHE_PREFIX = "pay:detail:";

    /**
     * 支付单缓存过期时间（秒）- 5 分钟
     */
    private static final int PAY_CACHE_EXPIRE_SECONDS = 300;

    /**
     * 空值缓存过期时间（秒）- 60 秒，防穿透
     */
    private static final int NULL_CACHE_EXPIRE_SECONDS = 60;

    /**
     * 支付回调幂等键 TTL（秒）- 24 小时
     */
    private static final long CALLBACK_IDEMPOTENT_TTL = 24 * 3600L;

    // ==================== DB 读写 ====================

    /**
     * 插入支付单。
     *
     * @param pay 支付单实体
     * @return 影响行数
     */
    public int insert(Pay pay) {
        return payMapper.insert(pay);
    }

    /**
     * 根据支付单 ID 查询（先缓存后 DB）。
     *
     * @param payId 支付单 ID
     * @return 支付单信息；不存在返回 null
     */
    public Pay getById(Long payId) {
        // 1. 先查缓存
        String cacheKey = PAY_CACHE_PREFIX + payId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("NULL".equals(cached)) {
                return null;
            }
            return JSON.parseObject(cached, Pay.class);
        }

        // 2. 缓存未命中，查 DB
        Pay pay = payMapper.selectById(payId);

        // 3. 写入缓存（空值也缓存，短 TTL 防穿透）
        cachePay(cacheKey, pay);
        return pay;
    }

    /**
     * 更新支付单（先清缓存再更新，保证一致性）。
     *
     * @param pay 支付单实体
     * @return 影响行数
     */
    public int update(Pay pay) {
        evictCache(pay.getId());
        return payMapper.updateById(pay);
    }

    /**
     * 根据订单 ID 查询支付单（按 orderId 维度缓存）。
     *
     * @param orderId 订单 ID
     * @return 支付单信息；不存在返回 null
     */
    public Pay getByOrderId(Long orderId) {
        // 1. 先查缓存
        String cacheKey = PAY_CACHE_PREFIX + "order:" + orderId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("NULL".equals(cached)) {
                return null;
            }
            return JSON.parseObject(cached, Pay.class);
        }

        // 2. 缓存未命中，查 DB
        LambdaQueryWrapper<Pay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Pay::getOrderId, orderId).last("LIMIT 1");
        Pay pay = payMapper.selectOne(wrapper);

        // 3. 写入缓存
        cachePay(cacheKey, pay);
        return pay;
    }

    /**
     * 根据订单 ID 和用户 ID 查询支付单（校验归属，分片键命中高效）。
     *
     * @param orderId 订单 ID
     * @param userId  用户 ID
     * @return 支付单信息；不存在返回 null
     */
    public Pay getByOrderIdAndUserId(Long orderId, Long userId) {
        LambdaQueryWrapper<Pay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Pay::getOrderId, orderId)
                .eq(Pay::getUserId, userId)
                .last("LIMIT 1");
        return payMapper.selectOne(wrapper);
    }

    /**
     * 对账任务查询：扫描「待支付且未回调」的支付单（PRD §7.4.3 UC-PAY-03）。
     *
     * <p>条件：status=待支付 且 callbackTime 为空；按过期时间升序取最近 limit 条。
     *
     * @param limit 查询数量上限
     * @return 待对账支付单列表
     */
    public List<Pay> listPendingCallback(int limit) {
        LambdaQueryWrapper<Pay> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Pay::getStatus, 1)
                .isNull(Pay::getCallbackTime)
                .orderByAsc(Pay::getExpireTime)
                .last("LIMIT " + limit);
        return payMapper.selectList(wrapper);
    }

    /**
     * 清除支付单缓存。
     *
     * @param payId 支付单 ID
     */
    public void evictCache(Long payId) {
        stringRedisTemplate.delete(PAY_CACHE_PREFIX + payId);
    }

    /**
     * 清除按 orderId 维度的支付单缓存。
     *
     * @param orderId 订单 ID
     */
    public void evictCacheByOrderId(Long orderId) {
        stringRedisTemplate.delete(PAY_CACHE_PREFIX + "order:" + orderId);
    }

    // ==================== 支付回调幂等键 ====================

    /**
     * 消费支付回调幂等键（Redis GETDEL 原子操作）。
     *
     * <p>PRD §10.5 幂等性：回调可能重复投递，以 pay_no 为幂等键。
     * 首次调用返回 true，重复调用返回 false（键已被删除）。
     *
     * @param payNo 支付单号（回调幂等键）
     * @return true=首次消费可继续处理；false=重复回调应跳过
     */
    public boolean tryConsumeCallbackIdempotent(String payNo) {
        String key = String.format(RedisKeyConstant.PAY_CALLBACK_IDEMPOTENT, payNo);
        // setIfAbsent 仅在键不存在时写入成功；用此语义标记「正在处理」
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(System.currentTimeMillis()),
                        CALLBACK_IDEMPOTENT_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(first);
    }

    /**
     * 主动标记支付回调已处理（用于对账等内部链路补登记幂等键）。
     *
     * @param payNo 支付单号
     */
    public void markCallbackProcessed(String payNo) {
        String key = String.format(RedisKeyConstant.PAY_CALLBACK_IDEMPOTENT, payNo);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()),
                CALLBACK_IDEMPOTENT_TTL, TimeUnit.SECONDS);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 写入支付单缓存（含空值缓存防穿透）。
     *
     * @param cacheKey 缓存 Key
     * @param pay      支付单；为 null 时写入空值标记
     */
    private void cachePay(String cacheKey, Pay pay) {
        if (pay != null) {
            stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(pay),
                    PAY_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "NULL",
                    NULL_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 获取对账任务幂等键 TTL（供 Scheduler 复用）。
     *
     * @return 幂等键 TTL（秒）
     */
    public long getCallbackIdempotentTtl() {
        return CALLBACK_IDEMPOTENT_TTL;
    }

    /**
     * 判断支付单是否已过期。
     *
     * @param pay 支付单
     * @return true=已过期（当前时间超过 expireTime）
     */
    public boolean isExpired(Pay pay) {
        return pay != null && pay.getExpireTime() != null
                && LocalDateTime.now().isAfter(pay.getExpireTime());
    }
}
