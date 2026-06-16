package com.damai.api.fallback.order;

import com.damai.api.client.order.OrderFeignClient;
import com.damai.api.dto.order.OrderDto;
import com.damai.common.api.ApiResult;
import com.damai.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单 Feign 客户端降级实现 —— order 服务不可达时兜底。
 */
@Slf4j
@Component
public class OrderFeignClientFallback implements OrderFeignClient {

    @Override
    public ApiResult<OrderDto> getById(Long orderId) {
        log.warn("[OrderFeignFallback] getById 降级触发, orderId={}", orderId);
        return ApiResult.fail(ErrorCode.RPC_INVOKE_ERROR, "订单服务暂不可用");
    }
}
