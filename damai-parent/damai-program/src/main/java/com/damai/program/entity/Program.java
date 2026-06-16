package com.damai.program.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节目实体
 *
 * @author damai
 */
@Data
@TableName("t_program")
public class Program implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 节目ID（主键，自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 节目标题
     */
    private String title;

    /**
     * 艺人
     */
    private String artist;

    /**
     * 场馆
     */
    private String venue;

    /**
     * 城市编码
     */
    private String cityCode;

    /**
     * 城市名
     */
    private String cityName;

    /**
     * 节目类型编码
     */
    private String typeCode;

    /**
     * 节目介绍
     */
    private String description;

    /**
     * 海报URL
     */
    private String posterUrl;

    /**
     * 最低票价
     */
    private BigDecimal priceMin;

    /**
     * 最高票价
     */
    private BigDecimal priceMax;

    /**
     * 演出开始时间（首场）
     */
    private LocalDateTime showStart;

    /**
     * 演出结束时间（末场）
     */
    private LocalDateTime showEnd;

    /**
     * 开售时间
     */
    private LocalDateTime saleStart;

    /**
     * 热度值
     */
    private Integer heat;

    /**
     * 节目状态：0-草稿，1-在售，2-已下架，3-已结束
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
