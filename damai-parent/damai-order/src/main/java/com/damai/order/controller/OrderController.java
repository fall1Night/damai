package com.damai.order.controller;

import com.damai.common.api.ApiResult;
import com.damai.common.api.PageResult;
import com.damai.order.entity.Order;
import com.damai.order.entity.OrderDetail;
import com.damai.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单控制器 —— 对应 PRD §8 抢票下单全链路。
 *
 * @author damai
 */
@Tag(name = "订单服务", description = "抢票下单、订单查询、取消订单")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;

    // ==================== 用户端接口 ====================

    /**
     * 领取幂等令牌
     */
    @Operation(summary = "领取幂等令牌", description = "用户进入下单页时调用，获取下单令牌")
    @PostMapping("/token")
    public ApiResult<String> generateToken(
            @RequestHeader("X-User-Id") Long userId) {
        String token = orderService.generateToken(userId);
        return ApiResult.success(token);
    }

    /**
     * 提交下单
     */
    @Operation(summary = "提交下单", description = "抢票下单核心接口（携带令牌+场次+票档+数量+观演人）")
    @PostMapping
    public ApiResult<CreateOrderResponse> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @Validated @RequestBody CreateOrderRequest request) {
        Map<String, Object> result = orderService.createOrder(
                userId,
                request.getShowId(),
                request.getTicketTypeId(),
                request.getQuantity(),
                request.getViewerInfo(),
                request.getToken()
        );

        CreateOrderResponse response = new CreateOrderResponse();
        response.setOrderId((Long) result.get("orderId"));
        response.setTotalAmount((BigDecimal) result.get("totalAmount"));
        response.setPayDeadline((LocalDateTime) result.get("payDeadline"));
        return ApiResult.success(response);
    }

    /**
     * 我的订单（按状态分页）
     */
    @Operation(summary = "我的订单", description = "按状态分页查询订单")
    @GetMapping("/list")
    public ApiResult<PageResult<Order>> listOrders(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "订单状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<Order> result = orderService.queryOrders(userId, status, pageNum, pageSize);
        return ApiResult.success(result);
    }

    /**
     * 订单详情
     */
    @Operation(summary = "订单详情", description = "获取订单详情（含明细）")
    @GetMapping("/{orderId}")
    public ApiResult<Map<String, Object>> orderDetail(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable Long orderId) {
        Map<String, Object> result = orderService.queryOrderDetail(userId, orderId);
        return ApiResult.success(result);
    }

    /**
     * 主动取消订单
     */
    @Operation(summary = "取消订单", description = "主动取消待支付订单")
    @PostMapping("/{orderId}/cancel")
    public ApiResult<Void> cancelOrder(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable Long orderId) {
        orderService.cancelOrder(userId, orderId);
        return ApiResult.success(null);
    }

    // ==================== 内部 Feign 接口 ====================

    /**
     * 内部接口：根据 orderId 查询订单（供 pay 服务调用）
     */
    @Operation(summary = "内部接口-查询订单", description = "供 pay 服务通过 Feign 调用")
    @GetMapping("/inner/getById")
    public ApiResult<Order> getById(
            @Parameter(description = "订单ID", required = true) @RequestParam Long orderId) {
        // 此处简化实现，实际应通过 OrderManager 查询
        return ApiResult.success(null);
    }

    // ==================== 请求体 DTO ====================

    @Data
    public static class CreateOrderRequest {
        @NotNull(message = "场次ID不能为空")
        private Long showId;

        @NotNull(message = "票档ID不能为空")
        private Long ticketTypeId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于0")
        private Integer quantity;

        @NotBlank(message = "观演人信息不能为空")
        private String viewerInfo;

        @NotBlank(message = "下单令牌不能为空")
        private String token;
    }

    @Data
    public static class CreateOrderResponse {
        private Long orderId;
        private BigDecimal totalAmount;
        private LocalDateTime payDeadline;
    }
}
