package com.damai.api.fallback.program;

import com.damai.api.client.program.ProgramFeignClient;
import com.damai.api.dto.program.ProgramDto;
import com.damai.api.dto.program.StockDeductRequest;
import com.damai.api.dto.program.StockDeductResult;
import com.damai.api.dto.program.TicketTypeDto;
import com.damai.common.api.ApiResult;
import com.damai.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 节目 Feign 客户端降级实现 —— program 服务不可达时兜底。
 *
 * <p>核心关注：库存预扣降级返回 {@code STOCK_NOT_ENOUGH}（视为失败），
 * 下单流程走"快速失败"分支，避免扣减成功但 DB 未落单的不一致。
 * 这比返回 SUCCESS（假装成功）安全得多。
 */
@Slf4j
@Component
public class ProgramFeignClientFallback implements ProgramFeignClient {

    @Override
    public ApiResult<StockDeductResult> deductStock(StockDeductRequest request) {
        log.warn("[ProgramFeignFallback] deductStock 降级触发, showId={}, ticketTypeId={}",
                request.getShowId(), request.getTicketTypeId());
        // 降级时视为库存不足，让下单流程快速失败
        return ApiResult.fail(ErrorCode.LOCK_ACQUIRE_FAIL, "系统繁忙，请稍后再试");
    }

    @Override
    public ApiResult<Boolean> refundStock(StockDeductRequest request) {
        log.warn("[ProgramFeignFallback] refundStock 降级触发, showId={}, ticketTypeId={}",
                request.getShowId(), request.getTicketTypeId());
        // 库存归还降级：记录日志，不抛异常。后续由对账任务补偿。
        return ApiResult.fail(ErrorCode.RPC_INVOKE_ERROR, "节目服务暂不可用，库存归还将延后补偿");
    }

    @Override
    public ApiResult<List<TicketTypeDto>> listTicketType(Long programId) {
        log.warn("[ProgramFeignFallback] listTicketType 降级触发, programId={}", programId);
        return ApiResult.fail(ErrorCode.RPC_INVOKE_ERROR, "节目服务暂不可用");
    }

    @Override
    public ApiResult<ProgramDto> getById(Long programId) {
        log.warn("[ProgramFeignFallback] getById 降级触发, programId={}", programId);
        return ApiResult.fail(ErrorCode.RPC_INVOKE_ERROR, "节目服务暂不可用");
    }
}
