package com.damai.api.dto.pay;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单传输对象 —— 对应 DDL {@code t_pay}。
 *
 * <p>用于服务间支付状态查询。order 服务通过此对象确认支付是否已完成。
 *
 * <p>状态码与 {@link com.damai.common.enums.PayStatusEnum} 对齐：
 * 1待支付 2成功 3失败 4已关闭 5已退款
 */
@Data
public class PayDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 支付单 ID */
    private Long id;

    /** 支付单号（业务唯一，回调幂等键） */
    private String payNo;

    /** 订单 ID（分片键，与 t_order 同分片组） */
    private Long orderId;

    /** 用户 ID */
    private Long userId;

    /** 支付金额（元） */
    private BigDecimal payAmount;

    /** 支付方式：1微信(模拟) 2支付宝(模拟) 3余额(模拟) */
    private Integer payMethod;

    /** 状态：1待支付 2成功 3失败 4已关闭 5已退款（PayStatusEnum） */
    private Integer status;

    /** 支付成功时间 */
    private LocalDateTime payTime;

    /** 支付单过期时间 */
    private LocalDateTime expireTime;

    private LocalDateTime createTime;
}
