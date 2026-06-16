package com.damai.program.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.program.entity.Program;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节目 Mapper
 *
 * @author damai
 */
@Mapper
public interface ProgramMapper extends BaseMapper<Program> {
}
