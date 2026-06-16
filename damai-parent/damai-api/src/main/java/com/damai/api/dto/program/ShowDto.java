package com.damai.api.dto.program;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 场次传输对象 —— 对应 DDL {@code t_show}。
 */
@Data
public class ShowDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long programId;
    /** 演出时间（如 2026-07-01 19:30:00） */
    private LocalDateTime showTime;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;
    /** 售卖模式：1票档级(先到先得) 2座位级(自选) */
    private Integer seatMode;
    /** 场次状态：0未开售 1在售 2已停售 3已结束 */
    private Integer status;
}
