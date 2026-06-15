package com.damai.starter.sentinel;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Sentinel 自动配置 —— 对应技术文档 §8.4。
 *
 * <p>Spring Cloud Alibaba Sentinel Starter 自动注册资源与规则，
 * 此处扩展：
 * <ul>
 *   <li>注册热点参数限流规则配置 Bean（{@link HotParamLimitConfig}）。</li>
 * </ul>
 *
 * <p>限流策略：
 * <ul>
 *   <li>网关层：全局 QPS 限流、IP/用户维度防刷（在 damai-gateway 中配置）。</li>
 *   <li>服务层：Sentinel 接口 QPS 限流 + <b>热点参数限流</b>
 *       （按 programId / ticketTypeId 限流，防单节目流量挤垮服务）。</li>
 *   <li>Feign 调用：熔断降级（在 damai-api fallback 中实现）。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.alibaba.csp.sentinel.SphU")
public class SentinelAutoConfiguration {

    @Bean
    public HotParamLimitConfig hotParamLimitConfig() {
        return new HotParamLimitConfig();
    }
}
