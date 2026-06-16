package com.damai.api.dto.program;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节目传输对象 —— 对应 DDL {@code t_program}，用于跨服务查询节目基础信息。
 *
 * <p>字段与表对齐，去除了 {@code is_deleted} 等内部字段。
 */
@Data
public class ProgramDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String artist;
    /** 节目类型编码（关联 t_program_type，如 concert/play） */
    private String typeCode;
    /** 城市编码 */
    private String cityCode;
    private String cityName;
    private String venue;
    private String venueAddress;
    private String description;
    private String posterUrl;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private LocalDateTime showStart;
    private LocalDateTime showEnd;
    private LocalDateTime saleStart;
    /** 状态：0草稿 1在售 2已下架 3已结束（ProgramStatusEnum） */
    private Integer status;
    private Integer heat;
}
