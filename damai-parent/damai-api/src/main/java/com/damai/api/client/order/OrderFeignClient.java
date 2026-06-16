package com.damai.api.client.order;

import com.damai.api.dto.order.OrderDto;
import com.damai.api.fallback.order.OrderFeignClientFallback;
import com.damai.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单服务 Feign 客户端 —— 供 pay 服务查询订单状态（支付回调时校验）。
 *
 * <p>pay 服务收到支付回调后，需通过此接口确认订单当前状态（是否仍为待支付），
 * 避免重复回调导致状态错乱（PRD §10.5 幂等性）。
 */
@FeignClient(name = "damai-order", fallback = OrderFeignClientFallback.class)
public interface OrderFeignClient {

    /**
     * 根据 orderId 查询订单（pay 服务支付回调时调用）。
     *
     * @param orderId 订单 ID
     * @return 订单信息
     */
    @GetMapping("/order/inner/getById")
    ApiResult<OrderDto> getById(@RequestParam("orderId") Long orderId);
}
