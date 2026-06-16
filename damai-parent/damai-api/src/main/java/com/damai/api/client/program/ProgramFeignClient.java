package com.damai.api.client.program;

import com.damai.api.dto.program.ProgramDto;
import com.damai.api.dto.program.StockDeductRequest;
import com.damai.api.dto.program.StockDeductResult;
import com.damai.api.dto.program.TicketTypeDto;
import com.damai.api.fallback.program.ProgramFeignClientFallback;
import com.damai.common.api.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 节目服务 Feign 客户端 —— 供 order/pay 等服务远程调用。
 *
 * <p>核心能力：库存预扣 / 归还 / 查询、节目详情查询。
 * 库存逻辑归属 program 服务（技术文档 §5.1 领域设计取舍：它最了解"场次+票档"语义）。
 *
 * <p>调用方约定：
 * <ul>
 *   <li>{@code /inner/**} 路径为服务间内部接口，网关不对外暴露，仅供 Feign 调用。</li>
 *   <li>库存预扣的原子性由 program 服务侧 Lua 脚本保证（技术文档 §9.1），
 *       调用方无需加锁，失败直接走业务异常。</li>
 * </ul>
 */
@FeignClient(name = "damai-program", fallback = ProgramFeignClientFallback.class)
public interface ProgramFeignClient {

    /**
     * 库存预扣（Lua 原子）—— 抢票下单核心。
     * <p>对应 PRD §8.1 第 4 步 / 技术文档 §9.1。
     * <p>调用方（order 服务）在幂等校验后、落单前调用。
     *
     * @param request 预扣请求（场次+票档+数量）
     * @return 预扣结果枚举（成功 / 库存不足 / 未预热）
     */
    @PostMapping("/program/inner/stock/deduct")
    ApiResult<StockDeductResult> deductStock(@RequestBody StockDeductRequest request);

    /**
     * 库存归还 —— 订单取消（手动/超时）时调用，归还预扣的库存。
     * <p>对应 PRD §8.3 归还库存、技术文档 §9（Redis 同步归还 + 异步 DB 归还）。
     *
     * @param request 归还请求（复用 StockDeductRequest 入参）
     * @return 是否归还成功
     */
    @PostMapping("/program/inner/stock/refund")
    ApiResult<Boolean> refundStock(@RequestBody StockDeductRequest request);

    /**
     * 查询指定节目下的票档列表（含 Redis 实时可售库存）。
     * <p>供 order 服务做下单前的库存预览与校验。
     *
     * @param programId 节目 ID
     * @return 票档列表
     */
    @GetMapping("/program/inner/ticketType/list")
    ApiResult<List<TicketTypeDto>> listTicketType(@RequestParam("programId") Long programId);

    /**
     * 查询节目基础信息。
     */
    @GetMapping("/program/inner/getById")
    ApiResult<ProgramDto> getById(@RequestParam("programId") Long programId);
}
