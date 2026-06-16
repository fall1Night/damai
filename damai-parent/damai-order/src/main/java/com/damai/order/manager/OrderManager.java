package com.damai.order.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.api.PageResult;
import com.damai.order.dao.LocalMessageMapper;
import com.damai.order.dao.OrderMapper;
import com.damai.order.entity.LocalMessage;
import com.damai.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单 Manager（DB 读写 + 本地消息表 + 缓存）
 *
 * <p>职责：
 * <ul>
 *   <li>订单 CRUD（ShardingSphere 分片路由自动命中）</li>
 *   <li>本地消息表写入（与业务表同事务，保证最终一致）</li>
 *   <li>订单状态查询缓存（短 TTL，防穿透）</li>
 * </ul>
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderManager {

    private final OrderMapper orderMapper;
    private final LocalMessageMapper localMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 订单缓存前缀
     */
    private static final String ORDER_CACHE_PREFIX = "order:detail:";

    /**
     * 订单缓存过期时间（秒）- 5 分钟
     */
    private static final int ORDER_CACHE_EXPIRE_SECONDS = 300;

    /**
     * 插入订单
     *
     * @param order 订单实体
     * @return 影响行数
     */
    public int insert(Order order) {
        return orderMapper.insert(order);
    }

    /**
     * 根据订单ID查询（先走缓存，再走 DB）
     *
     * @param orderId 订单ID
     * @return 订单信息
     */
    public Order getById(Long orderId) {
        // 1. 先查缓存
        String cacheKey = ORDER_CACHE_PREFIX + orderId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("NULL".equals(cached)) {
                return null;
            }
            return com.alibaba.fastjson2.JSON.parseObject(cached, Order.class);
        }

        // 2. 缓存未命中，查 DB
        Order order = orderMapper.selectById(orderId);

        // 3. 写入缓存
        if (order != null) {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    com.alibaba.fastjson2.JSON.toJSONString(order),
                    ORDER_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "NULL", 60, TimeUnit.SECONDS);
        }

        return order;
    }

    /**
     * 更新订单
     *
     * @param order 订单实体
     * @return 影响行数
     */
    public int update(Order order) {
        evictCache(order.getId());
        return orderMapper.updateById(order);
    }

    /**
     * 按用户ID和状态分页查询订单（命中分片，高效）
     *
     * @param userId   用户ID（分片键）
     * @param status   订单状态（可选）
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public PageResult<Order> listByUserId(Long userId, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId);
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        // 分页查询
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        orderMapper.selectPage(page, wrapper);

        return PageResult.of(page.getTotal(), page.getRecords(), pageNum, pageSize);
    }

    /**
     * 根据订单ID和用户ID查询（校验归属）
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @return 订单信息
     */
    public Order getByIdAndUserId(Long orderId, Long userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getId, orderId)
                .eq(Order::getUserId, userId);
        return orderMapper.selectOne(wrapper);
    }

    /**
     * 写入本地消息表（与业务表同事务）
     *
     * @param message 本地消息实体
     * @return 影响行数
     */
    public int insertLocalMessage(LocalMessage message) {
        return localMessageMapper.insert(message);
    }

    /**
     * 查询待发送的本地消息
     *
     * @param limit 查询数量
     * @return 待发送消息列表
     */
    public List<LocalMessage> listPendingMessages(int limit) {
        LambdaQueryWrapper<LocalMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocalMessage::getStatus, 0)
                .le(LocalMessage::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(LocalMessage::getCreateTime)
                .last("LIMIT " + limit);
        return localMessageMapper.selectList(wrapper);
    }

    /**
     * 更新本地消息状态
     *
     * @param message 本地消息实体
     * @return 影响行数
     */
    public int updateLocalMessage(LocalMessage message) {
        return localMessageMapper.updateById(message);
    }

    /**
     * 清除订单缓存
     *
     * @param orderId 订单ID
     */
    public void evictCache(Long orderId) {
        String cacheKey = ORDER_CACHE_PREFIX + orderId;
        stringRedisTemplate.delete(cacheKey);
    }
}
