package com.damai.common.constants;

/**
 * Redis Key 规范常量 —— 对应技术文档 §7.2 Redis 数据结构规划。
 *
 * <p>设计原则：
 * <ol>
 *   <li><b>统一前缀</b>：所有 Key 以 {@code damai:} 开头，避免与其它系统冲突。</li>
 *   <li><b>冒号分层</b>：业务域:子域:标识，例如 {@code damai:stock:showId:ticketTypeId}。</li>
 *   <li><b>占位符</b>：用 {@code %s} 标记动态段，调用方用 {@code String.format} 拼接。</li>
 *   <li><b>TTL 就近</b>：相关 TTL 常量定义在本类，便于统一调整。</li>
 * </ol>
 *
 * <p><b>注意</b>：库存类 Key 必须通过 Lua 脚本操作（技术文档 §9.1），保证原子性。
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {}

    /** 全局前缀 */
    public static final String PREFIX = "damai:";

    // ==================== 库存类（核心，需 Lua 原子操作） ====================

    /** 场次票档库存：damai:stock:{showId}:{ticketTypeId} */
    public static final String STOCK = PREFIX + "stock:%s:%s";

    /** 库存分布式锁：damai:lock:stock:{showId}:{ticketTypeId} */
    public static final String STOCK_LOCK = PREFIX + "lock:stock:%s:%s";

    // ==================== 幂等 / 防重类 ====================

    /** 下单幂等令牌：damai:idempotent:token:{token} */
    public static final String IDEMPOTENT_TOKEN = PREFIX + "idempotent:token:%s";

    /** 防重提交锁：damai:lock:submit:{userId}:{biz} */
    public static final String REPEAT_SUBMIT_LOCK = PREFIX + "lock:submit:%s:%s";

    /** 支付回调幂等：damai:idempotent:pay:callback:{payNo} */
    public static final String PAY_CALLBACK_IDEMPOTENT = PREFIX + "idempotent:pay:callback:%s";

    // ==================== 缓存类 ====================

    /** 节目详情：damai:program:detail:{programId} */
    public static final String PROGRAM_DETAIL = PREFIX + "program:detail:%s";

    /** 节目列表(分页)：damai:program:list:{channel}:{city}:{pageNum} */
    public static final String PROGRAM_LIST = PREFIX + "program:list:%s:%s:%s";

    /** 首页/频道缓存：damai:home:{channel}:{city} */
    public static final String HOME = PREFIX + "home:%s:%s";

    // ==================== 用户 / Session 类 ====================

    /** 用户 JWT Session：damai:session:jwt:{userId} */
    public static final String USER_SESSION = PREFIX + "session:jwt:%s";

    /** 短信验证码：damai:sms:code:{mobile} */
    public static final String SMS_CODE = PREFIX + "sms:code:%s";

    /** 短信发送频率限制：damai:sms:limit:{mobile} */
    public static final String SMS_LIMIT = PREFIX + "sms:limit:%s";

    /** 登录失败次数：damai:login:fail:{mobile} */
    public static final String LOGIN_FAIL_COUNT = PREFIX + "login:fail:%s";

    // ==================== 布隆过滤器类 ====================

    /** 节目存在性布隆过滤器：damai:bloom:program:exists */
    public static final String BLOOM_PROGRAM_EXISTS = PREFIX + "bloom:program:exists";

    /** 手机号唯一性布隆过滤器：damai:bloom:user:mobile */
    public static final String BLOOM_USER_MOBILE = PREFIX + "bloom:user:mobile";

    // ==================== TTL 常量（秒） ====================

    /** 节目详情 TTL：30 分钟 */
    public static final long TTL_PROGRAM_DETAIL = 30 * 60L;

    /** 首页 TTL：5 分钟 */
    public static final long TTL_HOME = 5 * 60L;

    /** 幂等令牌 TTL：10 分钟 */
    public static final long TTL_IDEMPOTENT_TOKEN = 10 * 60L;

    /** 防重锁 TTL：5 秒 */
    public static final long TTL_REPEAT_SUBMIT_LOCK = 5L;

    /** 短信验证码 TTL：5 分钟 */
    public static final long TTL_SMS_CODE = 5 * 60L;

    /** 短信频率限制 TTL：60 秒 */
    public static final long TTL_SMS_LIMIT = 60L;

    /** 登录失败计数窗口：15 分钟 */
    public static final long TTL_LOGIN_FAIL_COUNT = 15 * 60L;

    /** 用户 Session TTL：7 天 */
    public static final long TTL_USER_SESSION = 7 * 24 * 60 * 60L;

    /** 登录失败锁定阈值：5 次 */
    public static final int LOGIN_FAIL_LOCK_THRESHOLD = 5;
}
