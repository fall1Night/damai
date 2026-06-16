package com.damai.user.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 *
 * @author damai
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
