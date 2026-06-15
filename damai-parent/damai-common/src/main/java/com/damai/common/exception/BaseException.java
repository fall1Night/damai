package com.damai.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 系统所有自定义异常的基类。
 *
 * <p>设计思想：
 * <ul>
 *   <li>继承 {@link RuntimeException}（非受检异常），避免业务代码中到处 try-catch。</li>
 *   <li>携带 {@link ErrorCode}，全局异常处理器据此构造统一返回。</li>
 * </ul>
 *
 * @see BizException 业务异常（可预知）
 * @see SystemException 系统异常（不可预知）
 */
@Getter
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误码枚举 */
    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 快速获取业务码字符串。
     */
    public String getCode() {
        return errorCode.getCode();
    }
}
