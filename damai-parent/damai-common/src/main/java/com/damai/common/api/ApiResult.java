package com.damai.common.api;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一返回结果对象 —— 全系统所有 Controller 接口的唯一返回类型。
 *
 * <p>对应 PRD §11.1 统一返回结构，字段含义：
 * <ul>
 *   <li>{@link #code}：业务码，详见 {@link com.damai.common.exception.ErrorCode}</li>
 *   <li>{@link #message}：提示信息（面向用户的中文描述）</li>
 *   <li>{@link #data}：业务数据（泛型）</li>
 *   <li>{@link #traceId}：链路追踪 ID，便于全链路日志排查</li>
 *   <li>{@link #success}：是否成功（code == 0 即成功）</li>
 * </ul>
 *
 * <p>设计思想：
 * <ol>
 *   <li>不可变语义：通过静态工厂方法构造，避免外部随意 setter 造成状态混乱。</li>
 *   <li>泛型 {@code <T>}：兼容任意业务数据类型。</li>
 *   <li>实现 {@link Serializable}：支持 Feign 远程调用序列化、缓存序列化。</li>
 * </ol>
 *
 * @param <T> 业务数据类型
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 成功业务码 */
    public static final String CODE_SUCCESS = "0";

    /** 业务码 */
    private String code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 链路追踪 ID */
    private String traceId;

    /** 是否成功 */
    private boolean success;

    /**
     * 构造一个指定 code/message/data/success 的结果对象。
     * traceId 由调用方按需填充（通常在网关或全局过滤器注入）。
     */
    private ApiResult(String code, String message, T data, boolean success) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = success;
    }

    // ==================== 成功工厂方法 ====================

    public static <T> ApiResult<T> success() {
        return success(null);
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(CODE_SUCCESS, "success", data, true);
    }

    public static <T> ApiResult<T> success(T data, String message) {
        return new ApiResult<>(CODE_SUCCESS, message, data, true);
    }

    // ==================== 失败工厂方法 ====================

    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(code, message, null, false);
    }

    public static <T> ApiResult<T> fail(String code, String message, T data) {
        return new ApiResult<>(code, message, data, false);
    }

    /**
     * 基于错误码枚举构造失败结果。
     *
     * @param errorCode 错误码枚举（见 {@link com.damai.common.exception.ErrorCode}）
     */
    public static <T> ApiResult<T> fail(com.damai.common.exception.ErrorCode errorCode) {
        return new ApiResult<>(errorCode.getCode(), errorCode.getMessage(), null, false);
    }

    public static <T> ApiResult<T> fail(com.damai.common.exception.ErrorCode errorCode, String message) {
        return new ApiResult<>(errorCode.getCode(), message, null, false);
    }
}
