package com.damai.common.utils;

import com.damai.common.exception.BizException;
import com.damai.common.exception.ErrorCode;

import java.util.Collection;
import java.util.Map;

/**
 * 断言工具类 —— 简化业务代码中的参数/状态校验，失败时抛出 {@link BizException}。
 *
 * <p>设计思想（借鉴 Spring Assert / Guava Preconditions）：
 * <ul>
 *   <li>将"判断 + 抛异常"合并为一行，避免业务方法充斥 if-throw 样板代码。</li>
 *   <li>统一抛 {@link BizException}，由全局异常处理器转换为 {@link com.damai.common.api.ApiResult}。</li>
 *   <li>每个方法 ≤ 80 行，职责单一（约束 §3）。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   AssertUtil.notNull(user, ErrorCode.USER_NOT_FOUND);
 *   AssertUtil.isTrue(stock &gt;= quantity, ErrorCode.ORDER_STOCK_NOT_ENOUGH);
 *   AssertUtil.notEmpty(mobile, "手机号不能为空");
 * </pre>
 */
public final class AssertUtil {

    private AssertUtil() {}

    // ==================== 对象判空 ====================

    /**
     * 断言对象不为 null，否则抛业务异常。
     */
    public static void notNull(Object obj, ErrorCode errorCode) {
        if (obj == null) {
            throw new BizException(errorCode);
        }
    }

    public static void notNull(Object obj, String message) {
        if (obj == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
    }

    /**
     * 断言对象为 null（用于"应当不存在"的场景，如手机号注册前校验）。
     */
    public static void isNull(Object obj, ErrorCode errorCode) {
        if (obj != null) {
            throw new BizException(errorCode);
        }
    }

    // ==================== 布尔表达式 ====================

    /**
     * 断言表达式为 true，否则抛业务异常。
     * 业务校验最常用方法，例如库存校验、状态机校验。
     */
    public static void isTrue(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new BizException(errorCode);
        }
    }

    public static void isTrue(boolean expression, ErrorCode errorCode, String message) {
        if (!expression) {
            throw new BizException(errorCode, message);
        }
    }

    public static void isFalse(boolean expression, ErrorCode errorCode) {
        if (expression) {
            throw new BizException(errorCode);
        }
    }

    // ==================== 字符串判空 ====================

    public static void notEmpty(String str, ErrorCode errorCode) {
        if (str == null || str.isEmpty()) {
            throw new BizException(errorCode);
        }
    }

    public static void notBlank(String str, ErrorCode errorCode) {
        if (str == null || str.trim().isEmpty()) {
            throw new BizException(errorCode);
        }
    }

    public static void notBlank(String str, String message) {
        if (str == null || str.trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
    }

    // ==================== 集合判空 ====================

    public static void notEmpty(Collection<?> collection, ErrorCode errorCode) {
        if (collection == null || collection.isEmpty()) {
            throw new BizException(errorCode);
        }
    }

    public static void notEmpty(Map<?, ?> map, ErrorCode errorCode) {
        if (map == null || map.isEmpty()) {
            throw new BizException(errorCode);
        }
    }

    // ==================== 数值范围 ====================

    /**
     * 断言数值大于指定值（用于数量、金额等校验）。
     */
    public static void greaterThan(long value, long threshold, ErrorCode errorCode) {
        if (value <= threshold) {
            throw new BizException(errorCode);
        }
    }

    /**
     * 断言数值在 [min, max] 闭区间内（用于分页大小、限购数量等校验）。
     */
    public static void inRange(long value, long min, long max, ErrorCode errorCode) {
        if (value < min || value > max) {
            throw new BizException(errorCode);
        }
    }
}
