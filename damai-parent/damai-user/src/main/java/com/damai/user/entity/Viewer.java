package com.damai.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 观演人实体
 *
 * @author damai
 */
@Data
@TableName("t_viewer")
public class Viewer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 观演人ID（主键，自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（关联 t_user.id）
     */
    private Long userId;

    /**
     * 观演人姓名
     */
    private String name;

    /**
     * 证件类型：1-身份证，2-护照
     */
    private Integer idType;

    /**
     * 证件号码（加密存储）
     */
    private String idNo;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 是否默认观演人：0-否，1-是
     */
    private Integer isDefault;

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
