package com.damai.common.exception;

import java.io.Serial;

/**
 * 系统异常 —— 表示"不可预知的系统级错误"，例如：DB 连接失败、第三方调用超时、空指针。
 *
 * <p>这类异常通常代表系统出现真实故障，全局异常处理器应：
 * <ol>
 *   <li>记录 ERROR 日志（含完整堆栈，便于排查）。</li>
 *   <li>对外返回统一的 {@link ErrorCode#SYSTEM_ERROR}（不暴露内部细节）。</li>
 * </ol>
 */
public class SystemException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SystemException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public SystemException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
