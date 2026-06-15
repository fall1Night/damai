package com.damai.common.exception;

import java.io.Serial;

/**
 * 业务异常 —— 表示"可预知的业务错误"，例如：手机号已注册、库存不足、订单状态非法。
 *
 * <p>这类异常预期内发生，全局异常处理器应返回对应业务码与中文提示，
 * <b>不</b>记录 ERROR 日志（避免日志噪声），仅 DEBUG 级别。
 *
 * <p>使用示例：
 * <pre>
 * if (stock &lt; quantity) {
 *     throw new BizException(ErrorCode.ORDER_STOCK_NOT_ENOUGH);
 * }
 * </pre>
 */
public class BizException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BizException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BizException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
