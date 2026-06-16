package com.damai.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.order.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细 Mapper（分片表，与 t_order 同分片组）
 *
 * @author damai
 */
@Mapper
public interface OrderDetailMapper extends BaseMapper<OrderDetail> {
}
