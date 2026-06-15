package com.damai.starter.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等令牌注解 —— 对应技术文档 §10.1 下单幂等（令牌机制）。
 *
 * <p>使用方式：在需要幂等保护的 Controller 方法上标注此注解。
 * 切面会从请求参数或 Header 中提取 token，执行 Redis GETDEL 原子消费。
 *
 * <p>业务流程（PRD §10.5）：
 * <pre>
 *   1. 前端进入下单页 → 调 /order/token 领取幂等令牌 (UUID)
 *   2. token 存 Redis: idempotent:token:{token} = userId, TTL=10min
 *   3. 提交下单时携带 token → 切面原子消费 token（GETDEL）
 *   4. 返回值存在=首次提交=放行；nil=重复提交=拒绝
 * </pre>
 *
 * <p>使用示例：
 * <pre>
 *   // Controller 方法上标注
 *   &#64;PostMapping("/order")
 *   &#64;IdempotentToken(paramName = "token")
 *   public ApiResult&lt;OrderCreateVO&gt; createOrder(@RequestBody OrderCreateRequest request) {
 *       ...
 *   }
 * </pre>
 *
 * @see IdempotentTokenAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentToken {

    /**
     * 请求参数中 token 参数的名称。
     * <p>切面会从请求中提取此名称对应的值作为幂等令牌。
     *
     * @return 参数名（默认 "token"）
     */
    String paramName() default "token";
}
