package com.damai.common.utils;

import java.util.UUID;

/**
 * 链路追踪 ID 工具类 —— 对应技术文档 §12.2 全链路 traceId。
 *
 * <p>背景：
 * <ul>
 *   <li>网关在入口生成 traceId，写入响应头与 MDC（日志上下文）。</li>
 *   <li>下游服务/Feign/MQ 消费者透传同一 traceId，实现全链路日志串联。</li>
 *   <li>异常返回的 {@link com.damai.common.api.ApiResult#getTraceId()} 即此值，
 *       用户报障时提供 traceId 可一键检索完整链路。</li>
 * </ul>
 *
 * <p>注意：本类仅提供 traceId 的生成与 ThreadLocal 暂存能力，
 * 真正的"网关注入 + Feign 透传 + MDC 绑定"由
 * {@code damai-service-starter} 的 {@code TraceIdFilter} 完成。
 */
public final class TraceIdUtil {

    private TraceIdUtil() {}

    /** ThreadLocal 暂存当前请求的 traceId，便于业务代码随时获取 */
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    /**
     * 生成一个新的 traceId（32 位无横线 UUID，紧凑且全局唯一）。
     *
     * @return traceId 字符串
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置当前线程的 traceId（通常由 Filter 在请求入口调用）。
     */
    public static void set(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    /**
     * 获取当前线程的 traceId；若未设置则现场生成一个并绑定。
     * 业务代码可在任意位置安全调用，保证总有 traceId 输出。
     */
    public static String get() {
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null || traceId.isEmpty()) {
            traceId = generate();
            TRACE_ID_HOLDER.set(traceId);
        }
        return traceId;
    }

    /**
     * 清除当前线程的 traceId（必须由 Filter 在请求结束时调用，
     * 避免线程池复用导致的 traceId 串号）。
     */
    public static void clear() {
        TRACE_ID_HOLDER.remove();
    }
}
