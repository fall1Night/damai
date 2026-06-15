package com.damai.starter.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 热点参数限流规则配置 —— 对应技术文档 §8.4 热点参数限流。
 *
 * <p><b>核心场景</b>：抢票时一个热门节目的流量可能挤垮整个订单服务。
 * 通过按 {@code programId} 维度独立限流，不同节目的请求互不影响。
 *
 * <p>使用方式：在 order 服务下单接口上标注 Sentinel 注解：
 * <pre>
 *   &#64;SentinelResource(value = "createOrder", blockHandler = "createOrderBlockHandler")
 *   public ApiResult&lt;OrderCreateVO&gt; createOrder(...) {
 *       // 传入 programId 作为热点参数
 *       Entry entry = SphU.entry("createOrder", EntryType.IN, 1, programId);
 *       try { ... } finally { entry.exit(); }
 *   }
 * </pre>
 *
 * <p>规则说明：
 * <ul>
 *   <li>{@code createOrder}：下单接口，参数索引 0（programId），单热点阈值 500 QPS，
 *       总体阈值 5000 QPS。</li>
 *   <li>阈值可根据压测结果动态调整，或通过 Sentinel Dashboard 热更新。</li>
 * </ul>
 *
 * @see SentinelAutoConfiguration
 */
@Slf4j
public class HotParamLimitConfig {

    /** 下单接口热点参数限流：单节目 QPS 阈值（可按压测调整） */
    private static final int CREATE_ORDER_SINGLE_PROGRAM_QPS = 500;

    /** 下单接口总体 QPS 阈值 */
    private static final int CREATE_ORDER_TOTAL_QPS = 5000;

    /** 查询节目详情热点参数限流：单节目 QPS 阈值 */
    private static final int PROGRAM_DETAIL_SINGLE_QPS = 2000;

    /** 库存预扣热点参数限流：单票档 QPS 阈值 */
    private static final int STOCK_DEDUCT_SINGLE_QPS = 1000;

    @PostConstruct
    public void initRules() {
        List<ParamFlowRule> rules = new ArrayList<>();

        // 规则1：下单接口 —— 按 programId（参数索引 0）热点限流
        ParamFlowRule createOrderRule = new ParamFlowRule("createOrder")
                .setParamIdx(0)
                .setCount(CREATE_ORDER_SINGLE_PROGRAM_QPS)
                .setGrade(1); // QPS 级别
        rules.add(createOrderRule);

        // 规则2：节目详情 —— 按 programId 热点限流
        ParamFlowRule programDetailRule = new ParamFlowRule("programDetail")
                .setParamIdx(0)
                .setCount(PROGRAM_DETAIL_SINGLE_QPS)
                .setGrade(1);
        rules.add(programDetailRule);

        // 规则3：库存预扣 —— 按 ticketTypeId 热点限流
        ParamFlowRule stockDeductRule = new ParamFlowRule("stockDeduct")
                .setParamIdx(0)
                .setCount(STOCK_DEDUCT_SINGLE_QPS)
                .setGrade(1);
        rules.add(stockDeductRule);

        ParamFlowRuleManager.loadRules(rules);
        log.info("[Sentinel] 热点参数限流规则加载完成: createOrder={}/single, programDetail={}/single, stockDeduct={}/single",
                CREATE_ORDER_SINGLE_PROGRAM_QPS, PROGRAM_DETAIL_SINGLE_QPS, STOCK_DEDUCT_SINGLE_QPS);
    }
}
