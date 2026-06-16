package com.damai.program.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 票档实体
 *
 * @author damai
 */
@Data
@TableName("t_ticket_type")
public class TicketType implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 票档ID（主键，自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 场次ID（关联 t_show.id）
     */
    private Long showId;

    /**
     * 票档名称（如：VIP、一等座、二等座）
     */
    private String name;

    /**
     * 票价
     */
    private BigDecimal price;

    /**
     * 总库存
     */
    private Integer totalStock;

    /**
     * 可售库存（总库存 - 已售 - 锁定）
     */
    private Integer saleStock;

    /**
     * 锁定库存（预扣但未支付）
     */
    private Integer lockedStock;

    /**
     * 票档状态：0-停售，1-在售
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
