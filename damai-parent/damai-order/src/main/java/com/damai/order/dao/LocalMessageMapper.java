package com.damai.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.order.entity.LocalMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地消息 Mapper（非分片表）
 *
 * @author damai
 */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {
}
