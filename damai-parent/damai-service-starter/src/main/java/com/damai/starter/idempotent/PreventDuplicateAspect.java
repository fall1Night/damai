package com.damai.starter.idempotent;

import com.damai.common.constants.HeaderConstant;
import com.damai.common.constants.RedisKeyConstant;
import com.damai.common.exception.BizException;
import com.damai.common.exception.ErrorCode;
import com.damai.common.utils.AssertUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 防重提交锁 AOP 切面 —— 对应技术文档 §10.2。
 *
 * <p>核心逻辑：
 * <ol>
 *   <li>从请求 Header 中提取 {@code userId}（网关注入）。</li>
 *   <li>构造防重 Key：{@code damai:lock:submit:{userId}:{prefix}}。</li>
 *   <li>尝试 setNX（SET IF NOT EXISTS），成功 = 首次请求 → 放行。</li>
 *   <li>失败 = 短窗口内重复请求 → 拒绝，抛 {@link BizException}。</li>
 * </ol>
 *
 * <p>与 {@link IdempotentTokenAspect} 的区别：
 * <table>
 *   <tr><th></th><th>IdempotentToken</th><th>PreventDuplicate</th></tr>
 *   <tr><td>防重复类型</td><td>逻辑重复（同令牌）</td><td>物理连点（同用户同接口）</td></tr>
 *   <tr><td>粒度</td><td>每次下单需领新令牌</td><td>短窗口内自动拦截</td></tr>
 *   <tr><td>TTL</td><td>10min（令牌有效期）</td><td>5s（可配置，短窗口）</td></tr>
 *   <tr><td>执行顺序</td><td>先（Order=HIGHEST_PRECEDENCE+1）</td><td>后（Order=HIGHEST_PRECEDENCE+2）</td></tr>
 * </table>
 *
 * @see PreventDuplicate
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class PreventDuplicateAspect {

    private final RedissonClient redissonClient;

    public PreventDuplicateAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(preventDuplicate)")
    public Object around(ProceedingJoinPoint joinPoint, PreventDuplicate preventDuplicate) throws Throwable {
        // 1. 从 Header 提取 userId
        String userId = extractUserId();

        // 2. 构造防重 Key
        String redisKey = RedisKeyConstant.REPEAT_SUBMIT_LOCK.formatted(
                userId, preventDuplicate.prefix());

        // 3. 尝试 setNX（SET IF NOT EXISTS）
        RBucket<String> bucket = redissonClient.getBucket(redisKey);
        boolean acquired = bucket.trySet("1", preventDuplicate.expireSeconds(), TimeUnit.SECONDS);

        if (!acquired) {
            log.info("[PreventDuplicate] 短窗口内重复请求被拦截: userId={}, prefix={}, ttl={}s",
                    userId, preventDuplicate.prefix(), preventDuplicate.expireSeconds());
            throw new BizException(ErrorCode.ORDER_REPEAT_SUBMIT, "操作过于频繁，请稍后再试");
        }

        // 4. 放行，执行业务方法
        try {
            return joinPoint.proceed();
        } finally {
            // 5. 业务执行完毕后主动释放锁（可选：让 TTL 自然过期也行，此处提前释放提升吞吐）
            bucket.delete();
        }
    }

    /**
     * 从请求 Header 中提取 userId（网关解析 JWT 后透传）。
     */
    private String extractUserId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "anonymous";
        }
        HttpServletRequest request = attributes.getRequest();
        String userId = request.getHeader(HeaderConstant.USER_ID);
        return (userId != null && !userId.isEmpty()) ? userId : "anonymous";
    }
}
