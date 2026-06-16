package com.damai.api.fallback.pay;

import com.damai.api.client.pay.PayFeignClient;
import com.damai.api.dto.pay.PayDto;
import com.damai.common.api.ApiResult;
import com.damai.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 支付 Feign 客户端降级实现 —— pay 服务不可达时兜底。
 */
@Slf4j
@Component
public class PayFeignClientFallback implements PayFeignClient {

    @Override
    public ApiResult<PayDto> getByOrderId(Long orderId) {
        log.warn("[PayFeignFallback] getByOrderId 降级触发, orderId={}", orderId);
        return ApiResult.fail(ErrorCode.RPC_INVOKE_ERROR, "支付服务暂不可用");
    }
}
