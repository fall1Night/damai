package com.damai.program.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 场次实体
 *
 * @author damai
 */
@Data
@TableName("t_show")
public class Show implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场次ID（主键，自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 节目ID（关联 t_program.id）
     */
    private Long programId;

    /**
     * 场次名称（如：2026-07-01 19:30 场）
     */
    private String name;

    /**
     * 演出时间
     */
    private LocalDateTime showTime;

    /**
     * 开售时间
     */
    private LocalDateTime saleStart;

    /**
     * 场次状态：0-未开售，1-在售，2-已结束
     */
    private Integer status;

    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
