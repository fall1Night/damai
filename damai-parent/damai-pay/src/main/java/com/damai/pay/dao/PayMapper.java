package com.damai.pay.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.pay.entity.Pay;

/**
 * 支付单 Mapper —— 仅与 DB 交互，不含业务逻辑（约束 §6.2）。
 *
 * @author damai
 */
public interface PayMapper extends BaseMapper<Pay> {
}
