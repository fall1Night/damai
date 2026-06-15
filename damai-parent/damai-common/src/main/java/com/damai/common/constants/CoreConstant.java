package com.damai.common.constants;

/**
 * 核心常量 —— 系统级通用常量集合。
 */
public final class CoreConstant {

    private CoreConstant() {}

    // ==================== 分页 ====================

    /** 默认页码（从 1 开始） */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大每页大小（防止恶意大分页） */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 字符编码 / 时间 ====================

    /** 默认字符编码 */
    public static final String CHARSET_UTF8 = "UTF-8";

    /** 默认时区（展示用） */
    public static final String ZONE_ASIA_SHANGHAI = "Asia/Shanghai";

    // ==================== 订单业务常量 ====================

    /** 订单超时时间：15 分钟（毫秒） */
    public static final long ORDER_TIMEOUT_MS = 15 * 60 * 1000L;

    /** 单用户单节目限购数量（默认） */
    public static final int DEFAULT_PURCHASE_LIMIT = 4;

    // ==================== 通用成功标记 ====================

    public static final String STR_SUCCESS = "success";

    /** 系统默认 locale */
    public static final String DEFAULT_LOCALE = "zh_CN";
}
