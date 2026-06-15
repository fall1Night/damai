package com.damai.starter.web;

import com.damai.common.constants.HeaderConstant;
import com.damai.common.utils.TraceIdUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;

/**
 * traceId 拦截器（Servlet Filter 实现） —— 对应技术文档 §12.2 全链路 traceId。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>网关</b>在入口生成 traceId 写入 Header（{@code X-Trace-Id}）。</li>
 *   <li><b>下游业务服务</b>通过此 Filter 从 Header 提取 traceId，绑定到 ThreadLocal（MDC），</li>
 *   <li>请求结束后清理 ThreadLocal，防止线程池复用导致 traceId 串号。</li>
 *   <li>使用 Filter 而非 Interceptor，确保在所有 Servlet（含静态资源）层面都生效。</li>
 *   <li>设置最高优先级 {@link Ordered#HIGHEST_PRECEDENCE}，尽早绑定 traceId。</li>
 * </ul>
 *
 * <p>透传链路：网关生成 → Header → 本 Filter 绑定 ThreadLocal → 业务代码随时获取
 * → 日志 MDC 自动输出 → Feign/MQ 消费者透传。
 *
 * @see TraceIdUtil
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 1. 从上游 Header 提取 traceId（网关注入或 Feign 透传）
            String traceId = httpRequest.getHeader(HeaderConstant.TRACE_ID);

            // 2. 无 traceId 则生成新值（本地直接调用、未经过网关的场景）
            if (traceId == null || traceId.isEmpty()) {
                traceId = TraceIdUtil.generate();
            }

            // 3. 绑定到 ThreadLocal
            TraceIdUtil.set(traceId);

            // 4. 写入响应头（便于前端/调用方排查）
            httpResponse.setHeader(HeaderConstant.TRACE_ID, traceId);

            // 5. 写入 MDC（SLF4J / Logback 日志自动输出）
            setMdc(traceId);

            // 6. 继续执行后续 Filter / Controller
            chain.doFilter(request, response);

        } finally {
            // 7. 请求结束后必须清理，防止线程池复用串号
            TraceIdUtil.clear();
            clearMdc();
        }
    }

    /**
     * 将 traceId 写入 SLF4J MDC（Mapped Diagnostic Context）。
     * <p>Logback 配置中可直接使用 {@code %X{traceId}} 输出。
     */
    private void setMdc(String traceId) {
        try {
            org.slf4j.MDC.put("traceId", traceId);
        } catch (Exception e) {
            // MDC 不可用时降级，不影响主流程
            log.debug("[TraceIdFilter] MDC.put 失败: {}", e.getMessage());
        }
    }

    /**
     * 清理 MDC。
     */
    private void clearMdc() {
        try {
            org.slf4j.MDC.remove("traceId");
        } catch (Exception e) {
            log.debug("[TraceIdFilter] MDC.remove 失败: {}", e.getMessage());
        }
    }
}
