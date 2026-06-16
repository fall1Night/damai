package com.damai.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper（分片表，ShardingSphere 自动路由）
 *
 * @author damai
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
