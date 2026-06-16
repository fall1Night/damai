package com.damai.program.controller;

import com.damai.common.api.ApiResult;
import com.damai.common.api.PageResult;
import com.damai.program.entity.Program;
import com.damai.program.entity.Show;
import com.damai.program.service.ProgramService;
import com.damai.program.stock.StockManager;
import com.damai.program.stock.StockPreheatService;
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

import java.util.List;
import java.util.Map;

/**
 * 节目控制器
 *
 * @author damai
 */
@Tag(name = "节目服务", description = "节目浏览、搜索、详情、库存管理")
@RestController
@RequestMapping("/program")
@RequiredArgsConstructor
@Validated
public class ProgramController {

    private final ProgramService programService;
    private final StockPreheatService stockPreheatService;
    private final StockManager stockManager;

    /**
     * 首页推荐
     */
    @Operation(summary = "首页推荐", description = "获取首页推荐节目")
    @GetMapping("/home")
    public ApiResult<List<Program>> homeRecommend(
            @Parameter(description = "城市编码") @RequestParam(required = false) String city,
            @Parameter(description = "频道（节目类型编码）") @RequestParam(required = false) String channel) {
        List<Program> programs = programService.homeRecommend(city, channel);
        return ApiResult.success(programs);
    }

    /**
     * 分类浏览
     */
    @Operation(summary = "分类浏览", description = "按类型、城市筛选节目")
    @GetMapping("/list")
    public ApiResult<PageResult<Program>> listByCategory(
            @Parameter(description = "节目类型编码") @RequestParam(required = false) String typeCode,
            @Parameter(description = "城市编码") @RequestParam(required = false) String cityCode,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<Program> result = programService.listByCategory(typeCode, cityCode, pageNum, pageSize);
        return ApiResult.success(result);
    }

    /**
     * 智能搜索
     */
    @Operation(summary = "智能搜索", description = "关键词搜索节目（ES 分词）")
    @GetMapping("/search")
    public ApiResult<PageResult<Program>> search(
            @Parameter(description = "关键词", required = true) @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<Program> result = programService.search(keyword, pageNum, pageSize);
        return ApiResult.success(result);
    }

    /**
     * 节目详情
     */
    @Operation(summary = "节目详情", description = "获取节目详情（含场次、票档、实时库存）")
    @GetMapping("/{programId}")
    public ApiResult<Map<String, Object>> detail(
            @Parameter(description = "节目ID", required = true) @PathVariable Long programId) {
        Map<String, Object> result = programService.detail(programId);
        return ApiResult.success(result);
    }

    /**
     * 发布节目（运营端）
     */
    @Operation(summary = "发布节目", description = "发布新节目（运营端）")
    @PostMapping
    public ApiResult<Long> publish(@Validated @RequestBody PublishRequest request) {
        Program program = new Program();
        program.setTitle(request.getTitle());
        program.setArtist(request.getArtist());
        program.setVenue(request.getVenue());
        program.setCityCode(request.getCityCode());
        program.setCityName(request.getCityName());
        program.setTypeCode(request.getTypeCode());
        program.setDescription(request.getDescription());
        program.setPosterUrl(request.getPosterUrl());
        program.setPriceMin(request.getPriceMin());
        program.setPriceMax(request.getPriceMax());
        program.setShowStart(request.getShowStart());
        program.setShowEnd(request.getShowEnd());
        program.setSaleStart(request.getSaleStart());

        Long programId = programService.publish(program);
        return ApiResult.success(programId);
    }

    /**
     * 上架
     */
    @Operation(summary = "上架节目", description = "将节目设为在售状态")
    @PutMapping("/{programId}/on-shelf")
    public ApiResult<Void> onShelf(
            @Parameter(description = "节目ID", required = true) @PathVariable Long programId) {
        programService.onShelf(programId);
        return ApiResult.success(null);
    }

    /**
     * 下架
     */
    @Operation(summary = "下架节目", description = "将节目设为下架状态")
    @PutMapping("/{programId}/off-shelf")
    public ApiResult<Void> offShelf(
            @Parameter(description = "节目ID", required = true) @PathVariable Long programId) {
        programService.offShelf(programId);
        return ApiResult.success(null);
    }

    /**
     * 场次列表
     */
    @Operation(summary = "场次列表", description = "获取节目下的所有场次")
    @GetMapping("/{programId}/shows")
    public ApiResult<List<Show>> listShows(
            @Parameter(description = "节目ID", required = true) @PathVariable Long programId) {
        List<Show> shows = programService.listShows(programId);
        return ApiResult.success(shows);
    }

    /**
     * 票档列表（含实时库存）
     */
    @Operation(summary = "票档列表", description = "获取场次下的所有票档（含实时库存）")
    @GetMapping("/show/{showId}/ticket-types")
    public ApiResult<List<Map<String, Object>>> listTicketTypes(
            @Parameter(description = "场次ID", required = true) @PathVariable Long showId) {
        List<Map<String, Object>> result = programService.listTicketTypes(showId);
        return ApiResult.success(result);
    }

    // ==================== 管理端接口 ====================

    /**
     * 库存预热（管理端）
     */
    @Operation(summary = "库存预热", description = "触发指定节目库存预热（管理端）")
    @PostMapping("/stock/preheat")
    public ApiResult<Void> preheatStock(
            @Parameter(description = "节目ID", required = true) @RequestParam Long programId) {
        stockPreheatService.preheatProgram(programId);
        return ApiResult.success(null);
    }

    /**
     * 库存调整（管理端）
     */
    @Operation(summary = "库存调整", description = "调整票档库存（管理端）")
    @PostMapping("/stock/adjust")
    public ApiResult<Void> adjustStock(@Validated @RequestBody StockAdjustRequest request) {
        // 初始化库存
        stockManager.initStock(request.getShowId(), request.getTicketTypeId(), request.getStock());
        return ApiResult.success(null);
    }

    // ==================== 内部 Feign 接口 ====================

    /**
     * 内部接口：库存预扣（供订单服务调用）
     */
    @Operation(summary = "内部接口-库存预扣", description = "供订单服务通过 Feign 调用")
    @PostMapping("/inner/stock/deduct")
    public ApiResult<Long> deductStock(@Validated @RequestBody StockDeductRequest request) {
        Long result = stockManager.deductStock(request.getShowId(), request.getTicketTypeId(), request.getQuantity());
        return ApiResult.success(result);
    }

    /**
     * 内部接口：库存归还（供订单服务调用）
     */
    @Operation(summary = "内部接口-库存归还", description = "供订单服务通过 Feign 调用")
    @PostMapping("/inner/stock/refund")
    public ApiResult<Long> refundStock(@Validated @RequestBody StockRefundRequest request) {
        Long result = stockManager.refundStock(request.getShowId(), request.getTicketTypeId(), request.getQuantity());
        return ApiResult.success(result);
    }

    /**
     * 内部接口：查询票档列表（供订单服务调用）
     */
    @Operation(summary = "内部接口-查询票档列表", description = "供订单服务通过 Feign 调用")
    @GetMapping("/inner/ticket-type/list")
    public ApiResult<List<Map<String, Object>>> listTicketTypeInner(
            @Parameter(description = "场次ID", required = true) @RequestParam Long showId) {
        List<Map<String, Object>> result = programService.listTicketTypes(showId);
        return ApiResult.success(result);
    }

    /**
     * 内部接口：查询节目详情（供其他服务调用）
     */
    @Operation(summary = "内部接口-查询节目详情", description = "供其他服务通过 Feign 调用")
    @GetMapping("/inner/detail")
    public ApiResult<Map<String, Object>> detailInner(
            @Parameter(description = "节目ID", required = true) @RequestParam Long programId) {
        Map<String, Object> result = programService.detail(programId);
        return ApiResult.success(result);
    }

    // ==================== 请求体 DTO ====================

    @Data
    public static class PublishRequest {
        @NotBlank(message = "节目标题不能为空")
        private String title;
        private String artist;
        @NotBlank(message = "场馆不能为空")
        private String venue;
        @NotBlank(message = "城市编码不能为空")
        private String cityCode;
        private String cityName;
        @NotBlank(message = "节目类型编码不能为空")
        private String typeCode;
        private String description;
        private String posterUrl;
        private java.math.BigDecimal priceMin;
        private java.math.BigDecimal priceMax;
        private java.time.LocalDateTime showStart;
        private java.time.LocalDateTime showEnd;
        private java.time.LocalDateTime saleStart;
    }

    @Data
    public static class StockAdjustRequest {
        @NotNull(message = "场次ID不能为空")
        private Long showId;
        @NotNull(message = "票档ID不能为空")
        private Long ticketTypeId;
        @NotNull(message = "库存数量不能为空")
        @Min(value = 0, message = "库存数量不能为负")
        private Integer stock;
    }

    @Data
    public static class StockDeductRequest {
        @NotNull(message = "场次ID不能为空")
        private Long showId;
        @NotNull(message = "票档ID不能为空")
        private Long ticketTypeId;
        @NotNull(message = "扣减数量不能为空")
        @Min(value = 1, message = "扣减数量必须大于0")
        private Integer quantity;
    }

    @Data
    public static class StockRefundRequest {
        @NotNull(message = "场次ID不能为空")
        private Long showId;
        @NotNull(message = "票档ID不能为空")
        private Long ticketTypeId;
        @NotNull(message = "归还数量不能为空")
        @Min(value = 1, message = "归还数量必须大于0")
        private Integer quantity;
    }
}
