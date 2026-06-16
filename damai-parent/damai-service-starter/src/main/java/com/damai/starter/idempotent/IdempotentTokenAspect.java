package com.damai.starter.idempotent;

import com.damai.common.constants.RedisKeyConstant;
import com.damai.common.exception.BizException;
import com.damai.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 幂等令牌 AOP 切面 —— 对应技术文档 §10.1。
 *
 * <p>核心逻辑：
 * <ol>
 *   <li>从请求参数中提取 token（由 {@link IdempotentToken#paramName()} 指定）。</li>
 *   <li>通过 Redis Lua 脚本原子执行 GETDEL（读取 + 删除同一命令，保证原子性）。</li>
 *   <li>返回值存在 = 首次提交 → 放行。</li>
 *   <li>返回值不存在 = 重复提交 → 拒绝，抛 {@link BizException}。</li>
 * </ol>
 *
 * <p>为什么用 GETDEL 而非 GET + DEL？
 * <ul>
 *   <li>GET → 判断 → DEL 三步操作存在时间窗，并发下两个请求都读到有效值。</li>
 *   <li>GETDEL 在 Redis 中原子执行，杜绝竞态。</li>
 * </ul>
 *
 * @see IdempotentToken
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class IdempotentTokenAspect {

    private static final String LUA_GETDEL =
            "local val = redis.call('GET', KEYS[1]) " +
            "if val then redis.call('DEL', KEYS[1]) end " +
            "return val";

    private final RedissonClient redissonClient;

    public IdempotentTokenAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(idempotentToken)")
    public Object around(ProceedingJoinPoint joinPoint, IdempotentToken idempotentToken) throws Throwable {
        // 1. 从请求中提取 token
        String token = extractToken(idempotentToken.paramName());
        if (token == null || token.isEmpty()) {
            log.warn("[IdempotentToken] 请求中未携带幂等令牌，参数名={}", idempotentToken.paramName());
            throw new BizException(ErrorCode.ORDER_IDEMPOTENT_TOKEN_INVALID, "缺少幂等令牌");
        }

        // 2. 构造 Redis Key
        String redisKey = RedisKeyConstant.IDEMPOTENT_TOKEN.formatted(token);

        // 3. Lua 原子 GETDEL 消费令牌
        //    Redisson RScript 正确用法：getScript() 无参取实例，
        //    eval(Mode, script, ReturnType, keyList, args...) 分别传 KEYS 与 ARGV。
        Object result = redissonClient.getScript().eval(
                RScript.Mode.READ_WRITE,
                LUA_GETDEL,
                RScript.ReturnType.VALUE,
                List.of(redisKey)
        );

        // 4. 判断结果
        if (result == null) {
            log.info("[IdempotentToken] 令牌无效或已消费: token={}", token);
            throw new BizException(ErrorCode.ORDER_IDEMPOTENT_TOKEN_INVALID, "请勿重复提交");
        }

        String userId = result.toString();
        log.info("[IdempotentToken] 令牌消费成功: token={}, userId={}", token, userId);

        // 5. 放行，执行业务方法
        return joinPoint.proceed();
    }

    /**
     * 从请求参数中提取 token 值。
     * <p>优先从 query parameter / form parameter 中取，也支持从 Header 中取。
     */
    private String extractToken(String paramName) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        // 优先从参数取
        String token = request.getParameter(paramName);
        if (token != null && !token.isEmpty()) {
            return token;
        }
        // 降级从 Header 取
        return request.getHeader(paramName);
    }
}
