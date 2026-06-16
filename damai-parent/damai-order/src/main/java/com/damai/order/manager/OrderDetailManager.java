package com.damai.order.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.order.dao.OrderDetailMapper;
import com.damai.order.entity.OrderDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单明细 Manager（DB 读写）
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDetailManager {

    private final OrderDetailMapper orderDetailMapper;

    /**
     * 插入订单明细
     *
     * @param detail 订单明细实体
     * @return 影响行数
     */
    public int insert(OrderDetail detail) {
        return orderDetailMapper.insert(detail);
    }

    /**
     * 根据订单ID查询明细列表
     *
     * @param orderId 订单ID
     * @return 明细列表
     */
    public List<OrderDetail> listByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDetail::getOrderId, orderId);
        return orderDetailMapper.selectList(wrapper);
    }

    /**
     * 根据订单ID和用户ID查询明细列表（校验归属）
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @return 明细列表
     */
    public List<OrderDetail> listByOrderIdAndUserId(Long orderId, Long userId) {
        LambdaQueryWrapper<OrderDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDetail::getOrderId, orderId)
                .eq(OrderDetail::getUserId, userId);
        return orderDetailMapper.selectList(wrapper);
    }
}
