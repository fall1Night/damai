package com.damai.api.client.pay;

import com.damai.api.dto.pay.PayDto;
import com.damai.api.fallback.pay.PayFeignClientFallback;
import com.damai.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 支付服务 Feign 客户端 —— 供 order 服务查询支付状态。
 *
 * <p>order 服务的对账任务可通过此接口主动查询支付结果，
 * 补偿"支付已成功但回调丢失"的极端场景。
 */
@FeignClient(name = "damai-pay", fallback = PayFeignClientFallback.class)
public interface PayFeignClient {

    /**
     * 根据 orderId 查询支付单状态。
     *
     * @param orderId 订单 ID
     * @return 支付单信息
     */
    @GetMapping("/pay/inner/getByOrderId")
    ApiResult<PayDto> getByOrderId(@RequestParam("orderId") Long orderId);
}
