package com.damai.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码枚举 —— 对应 PRD §11.2 错误码体系（分级段位）。
 *
 * <p>段位约定：
 * <pre>
 *   0      —— 成功
 *   1xxxx  —— 通用错误（参数 / 权限 / 系统）
 *   2xxxx  —— 用户域错误
 *   3xxxx  —— 节目域错误
 *   4xxxx  —— 订单域错误
 *   5xxxx  —— 支付域错误
 *   9xxxx  —— 第三方 / 基础设施错误
 * </pre>
 *
 * <p>设计原则：
 * <ul>
 *   <li>业务可预知错误 → 明确业务码 + 中文提示。</li>
 *   <li>不可预知异常 → 全局异常处理器兜底返回 {@link #SYSTEM_ERROR}，并记录 traceId。</li>
 *   <li>同一段位预留扩展位，新增错误码按段位归类。</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ==================== 成功 ====================
    SUCCESS("0", "成功"),

    // ==================== 1xxxx 通用错误 ====================
    SYSTEM_ERROR("10000", "系统繁忙，请稍后再试"),
    PARAM_INVALID("10001", "参数非法"),
    PARAM_MISSING("10002", "参数缺失"),
    UNAUTHORIZED("10003", "未登录或登录已过期"),
    FORBIDDEN("10004", "无权限访问"),
    TOO_MANY_REQUESTS("10005", "请求过于频繁，请稍后再试"),
    NOT_FOUND("10006", "资源不存在"),
    METHOD_NOT_ALLOWED("10007", "请求方法不支持"),
    DATA_TYPE_ERROR("10008", "数据类型转换错误"),

    // ==================== 2xxxx 用户域错误 ====================
    USER_MOBILE_EXISTS("20001", "手机号已注册"),
    USER_NOT_FOUND("20002", "用户不存在"),
    USER_PASSWORD_ERROR("20003", "账号或密码错误"),
    USER_MOBILE_OR_PASSWORD_ERROR("20004", "手机号或密码错误"),
    USER_DISABLED("20005", "账号已被禁用"),
    USER_LOGIN_FAIL_LIMIT("20006", "登录失败次数过多，账号已被锁定 15 分钟"),
    SMS_CODE_ERROR("20007", "验证码错误或已过期"),
    SMS_CODE_SEND_TOO_FREQUENT("20008", "验证码发送过于频繁"),
    USER_TOKEN_INVALID("20009", "Token 无效"),
    USER_TOKEN_REFRESH_INVALID("20010", "RefreshToken 无效"),

    // ==================== 3xxxx 节目域错误 ====================
    PROGRAM_NOT_FOUND("30001", "节目不存在或已下架"),
    PROGRAM_OFF_SHELF("30002", "节目已下架"),
    PROGRAM_NOT_ON_SALE("30003", "节目尚未开售或已结束"),
    SHOW_NOT_FOUND("30004", "场次不存在"),
    TICKET_TYPE_NOT_FOUND("30005", "票档不存在"),
    STOCK_NOT_PREHEATED("30006", "库存尚未预热"),

    // ==================== 4xxxx 订单域错误 ====================
    ORDER_STOCK_NOT_ENOUGH("40001", "库存不足"),
    ORDER_REPEAT_SUBMIT("40002", "请勿重复提交订单"),
    ORDER_IDEMPOTENT_TOKEN_INVALID("40003", "下单令牌无效或已使用"),
    ORDER_NOT_FOUND("40004", "订单不存在"),
    ORDER_STATUS_NOT_ALLOW_CANCEL("40005", "订单当前状态不允许取消"),
    ORDER_STATUS_NOT_ALLOW_PAY("40006", "订单当前状态不允许支付"),
    ORDER_STATUS_NOT_ALLOW_QUERY("40007", "无权查看该订单"),
    ORDER_SEAT_LOCKED("40008", "所选座位已被锁定"),

    // ==================== 5xxxx 支付域错误 ====================
    PAY_ORDER_STATUS_INVALID("50001", "订单状态非法，无法发起支付"),
    PAY_REPEAT_CALLBACK("50002", "重复的支付回调"),
    PAY_CHANNEL_ERROR("50003", "支付通道异常"),
    PAY_AMOUNT_MISMATCH("50004", "支付金额与订单不匹配"),
    PAY_NOT_FOUND("50005", "支付单不存在"),

    // ==================== 9xxxx 第三方 / 基础设施错误 ====================
    REDIS_ERROR("90001", "缓存服务异常"),
    KAFKA_SEND_ERROR("90002", "消息发送失败"),
    ES_SEARCH_ERROR("90003", "搜索服务异常"),
    RPC_INVOKE_ERROR("90004", "远程调用失败"),
    LOCK_ACQUIRE_FAIL("90005", "系统繁忙，请稍后再试"),
    DB_ERROR("90006", "数据库服务异常");

    /** 业务码 */
    private final String code;

    /** 默认提示信息 */
    private final String message;
}
