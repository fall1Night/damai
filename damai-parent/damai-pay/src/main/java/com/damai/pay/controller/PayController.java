package com.damai.pay.controller;

import com.damai.api.dto.pay.PayDto;
import com.damai.common.api.ApiResult;
import com.damai.pay.entity.Pay;
import com.damai.pay.service.PayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付控制器 —— 对应 PRD §7.4 支付域用例。
 *
 * <p>包含用户端接口（发起支付/查询状态/退款）、支付方回调接口、内部 Feign 接口。
 *
 * @author damai
 */
@Tag(name = "支付服务", description = "发起支付、支付回调、查询支付状态、退款")
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
@Validated
public class PayController {

    private final PayService payService;

    // ==================== 用户端接口 ====================

    /**
     * 发起支付（模拟）。
     */
    @Operation(summary = "发起支付", description = "校验订单待支付后生成支付单，返回模拟支付参数")
    @PostMapping
    public ApiResult<Map<String, Object>> createPay(
            @RequestHeader("X-User-Id") Long userId,
            @Validated @RequestBody CreatePayRequest request) {
        Map<String, Object> result = payService.createPay(userId, request.getOrderId(), request.getPayMethod());
        return ApiResult.success(result);
    }

    /**
     * 支付结果回调（支付方调用，⭐核心幂等链路）。
     */
    @Operation(summary = "支付结果回调", description = "支付方（模拟）异步回调，更新支付单状态并通知订单")
    @PostMapping("/callback")
    public ApiResult<Void> payCallback(@Validated @RequestBody PayCallbackRequest request) {
        payService.payCallback(request.getOrderId(), request.getPaySuccess(),
                request.getPayNo(), request.getSign());
        return ApiResult.success(null);
    }

    /**
     * 查询支付状态。
     */
    @Operation(summary = "查询支付状态", description = "按 orderId 查询支付单状态")
    @GetMapping("/status")
    public ApiResult<PayDto> queryPayStatus(
            @Parameter(description = "订单ID", required = true) @RequestParam Long orderId) {
        PayDto dto = payService.queryPayStatus(orderId);
        return ApiResult.success(dto);
    }

    /**
     * 发起退款（模拟）。
     */
    @Operation(summary = "发起退款", description = "对已支付订单发起模拟退款")
    @PostMapping("/refund")
    public ApiResult<Void> refund(
            @RequestHeader("X-User-Id") Long userId,
            @Validated @RequestBody RefundRequest request) {
        payService.refund(userId, request.getOrderId());
        return ApiResult.success(null);
    }

    // ==================== 内部 Feign 接口 ====================

    /**
     * 内部接口：根据 orderId 查询支付单（供 order 服务对账通过 Feign 调用）。
     * <p>实现 {@code PayFeignClient} 约定的 {@code /pay/inner/getByOrderId} 端点。
     */
    @Operation(summary = "内部接口-查询支付单", description = "供 order 服务对账通过 Feign 调用")
    @GetMapping("/inner/getByOrderId")
    public ApiResult<PayDto> getByOrderId(
            @Parameter(description = "订单ID", required = true) @RequestParam Long orderId) {
        Pay pay = payService.getPayByOrderId(orderId);
        if (pay == null) {
            return ApiResult.success(null);
        }
        return ApiResult.success(convertToDto(pay));
    }

    // ==================== 请求体 DTO ====================

    @Data
    public static class CreatePayRequest {
        @NotNull(message = "订单ID不能为空")
        private Long orderId;

        @NotNull(message = "支付方式不能为空")
        @Min(value = 1, message = "支付方式非法")
        @Max(value = 3, message = "支付方式非法")
        private Integer payMethod;
    }

    @Data
    public static class PayCallbackRequest {
        @NotNull(message = "订单ID不能为空")
        private Long orderId;

        @NotNull(message = "支付结果不能为空")
        private Boolean paySuccess;

        @NotNull(message = "支付单号不能为空")
        private String payNo;

        /** 回调签名（模拟，可空） */
        private String sign;
    }

    @Data
    public static class RefundRequest {
        @NotNull(message = "订单ID不能为空")
        private Long orderId;
    }

    // ==================== 内部转换 ====================

    /**
     * 支付单实体转 DTO（内部接口返回用）。
     */
    private PayDto convertToDto(Pay pay) {
        PayDto dto = new PayDto();
        dto.setId(pay.getId());
        dto.setPayNo(pay.getPayNo());
        dto.setOrderId(pay.getOrderId());
        dto.setUserId(pay.getUserId());
        dto.setPayAmount(pay.getPayAmount());
        dto.setPayMethod(pay.getPayMethod());
        dto.setStatus(pay.getStatus());
        dto.setPayTime(pay.getPayTime());
        dto.setExpireTime(pay.getExpireTime());
        dto.setCreateTime(pay.getCreateTime());
        return dto;
    }
}
