package com.damai.starter.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重提交锁注解 —— 对应技术文档 §10.2 防重锁（短窗口）。
 *
 * <p>与 {@link IdempotentToken} 互补：
 * <ul>
 *   <li>令牌防"逻辑重复"（同一订单多次提交，token 消费后拒绝）；</li>
 *   <li>防重锁防"物理连点"（用户狂刷同一接口，短窗口内拒绝）。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   &#64;PostMapping("/order")
 *   &#64;PreventDuplicate(prefix = "order:create", expireSeconds = 5)
 *   public ApiResult&lt;OrderCreateVO&gt; createOrder(...) {
 *       ...
 *   }
 * </pre>
 *
 * @see PreventDuplicateAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreventDuplicate {

    /**
     * Redis Key 前缀。
     * <p>实际 Key = {@code damai:lock:submit:{userId}:{prefix}}
     * （对应 {@link com.damai.common.constants.RedisKeyConstant#REPEAT_SUBMIT_LOCK}）。
     *
     * @return 前缀（建议按业务操作命名，如 "order:create"）
     */
    String prefix() default "";

    /**
     * 锁的 TTL（秒）。
     * <p>默认 5 秒，防止用户短时间内重复提交。
     *
     * @return TTL 秒数
     */
    long expireSeconds() default 5;
}
