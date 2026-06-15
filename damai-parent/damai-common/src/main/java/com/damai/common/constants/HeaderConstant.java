package com.damai.common.constants;

/**
 * HTTP Header 常量 —— 用于网关与下游服务之间的信息透传。
 *
 * <p>背景（技术文档 §11.1）：网关统一解析 JWT，将 {@code userId}、角色等注入请求头，
 * 下游服务通过这些 Header 获取调用者信息，无需重复解析 JWT。
 *
 * <p>同时透传 {@code traceId}（技术文档 §12.2），保证全链路日志可串联。
 */
public final class HeaderConstant {

    private HeaderConstant() {}

    /** 用户 ID（网关解析 JWT 后透传） */
    public static final String USER_ID = "X-User-Id";

    /** 用户角色（多个用逗号分隔） */
    public static final String USER_ROLES = "X-User-Roles";

    /** 链路追踪 ID */
    public static final String TRACE_ID = "X-Trace-Id";

    /** 内部调用标记（服务间 Feign 调用携带，可绕过部分鉴权） */
    public static final String INTERNAL_CALL = "X-Internal-Call";

    /** 灰度路由标记（可选，用于灰度发布） */
    public static final String GRAY_TAG = "X-Gray-Tag";
}
