package com.damai.program.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.program.dao.TicketTypeMapper;
import com.damai.program.entity.TicketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 票档 Manager（缓存/DB 组合操作）
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketTypeManager {

    private final TicketTypeMapper ticketTypeMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 票档列表缓存前缀
     */
    private static final String TICKET_TYPE_LIST_CACHE_PREFIX = "program:ticket-types:";

    /**
     * 票档列表缓存过期时间（秒）- 30 分钟
     */
    private static final int TICKET_TYPE_LIST_CACHE_EXPIRE_SECONDS = 1800;

    /**
     * 根据场次ID查询票档列表（先走缓存，再走 DB）
     *
     * @param showId 场次ID
     * @return 票档列表
     */
    public List<TicketType> listByShowId(Long showId) {
        // 1. 先查缓存
        String cacheKey = TICKET_TYPE_LIST_CACHE_PREFIX + showId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("[]".equals(cached)) {
                return List.of();
            }
            return com.alibaba.fastjson2.JSON.parseArray(cached, TicketType.class);
        }

        // 2. 缓存未命中，查 DB
        LambdaQueryWrapper<TicketType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketType::getShowId, showId)
                .orderByAsc(TicketType::getPrice);
        List<TicketType> ticketTypes = ticketTypeMapper.selectList(wrapper);

        // 3. 写入缓存
        if (ticketTypes != null && !ticketTypes.isEmpty()) {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    com.alibaba.fastjson2.JSON.toJSONString(ticketTypes),
                    TICKET_TYPE_LIST_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "[]", 60, TimeUnit.SECONDS);
        }

        return ticketTypes;
    }

    /**
     * 根据票档ID查询
     *
     * @param ticketTypeId 票档ID
     * @return 票档信息
     */
    public TicketType getById(Long ticketTypeId) {
        return ticketTypeMapper.selectById(ticketTypeId);
    }

    /**
     * 插入票档
     *
     * @param ticketType 票档实体
     * @return 影响行数
     */
    public int insert(TicketType ticketType) {
        evictListCache(ticketType.getShowId());
        return ticketTypeMapper.insert(ticketType);
    }

    /**
     * 更新票档
     *
     * @param ticketType 票档实体
     * @return 影响行数
     */
    public int update(TicketType ticketType) {
        evictListCache(ticketType.getShowId());
        return ticketTypeMapper.updateById(ticketType);
    }

    /**
     * 更新库存（乐观锁）
     *
     * @param ticketTypeId 票档ID
     * @param quantity     扣减数量（正数扣减，负数归还）
     * @return 影响行数
     */
    public int updateStock(Long ticketTypeId, int quantity) {
        // 使用乐观锁更新：UPDATE t_ticket_type SET sale_stock = sale_stock - ? WHERE id = ? AND sale_stock >= ?
        // 这里使用 MyBatis-Plus 的方式实现
        TicketType ticketType = getById(ticketTypeId);
        if (ticketType == null) {
            return 0;
        }

        int newStock = ticketType.getSaleStock() - quantity;
        if (newStock < 0) {
            return 0; // 库存不足
        }

        ticketType.setSaleStock(newStock);
        return update(ticketType);
    }

    /**
     * 清除票档列表缓存
     *
     * @param showId 场次ID
     */
    public void evictListCache(Long showId) {
        String cacheKey = TICKET_TYPE_LIST_CACHE_PREFIX + showId;
        stringRedisTemplate.delete(cacheKey);
    }
}
