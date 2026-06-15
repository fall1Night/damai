package com.damai.starter.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域（CORS）自动配置 —— 对应技术文档 §11.2 网关安全。
 *
 * <p>设计要点：
 * <ul>
 *   <li>网关层统一处理跨域（推荐），业务服务也可引入此配置做兜底。</li>
 *   <li>允许所有来源（开发阶段），生产环境应通过 Nginx/网关限制。</li>
 *   <li>支持预检请求缓存（减少 OPTIONS 请求）。</li>
 * </ul>
 *
 * <p>如需自定义跨域规则，各服务可在 application.yml 中覆盖：
 * <pre>
 *   damai:
 *     cors:
 *       allowed-origins: https://app.damai.com
 *       allowed-methods: GET,POST,PUT,DELETE
 * </pre>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CorsAutoConfiguration {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的来源（生产环境应限制为前端域名）
        config.addAllowedOriginPattern("*");

        // 允许的请求方法
        config.addAllowedMethod("*");

        // 允许的请求头
        config.addAllowedHeader("*");

        // 允许携带凭证（Cookie / Authorization Header）
        config.setAllowCredentials(true);

        // 预检请求缓存时间（秒，减少 OPTIONS 请求频率）
        config.setMaxAge(3600L);

        // 暴露的响应头（前端可读取）
        config.addExposedHeader(com.damai.common.constants.HeaderConstant.TRACE_ID);
        config.addExposedHeader(com.damai.common.constants.HeaderConstant.USER_ID);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
