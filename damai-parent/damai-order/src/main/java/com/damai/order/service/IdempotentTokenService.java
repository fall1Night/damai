package com.damai.order.service;

import com.damai.common.constants.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 幂等令牌服务 —— 对应 PRD §10.5 幂等性设计。
 *
 * <p>令牌生命周期：
 * <ol>
 *   <li>生成：用户进入下单页时，调用 {@link #generateToken(Long)} 生成 UUID 令牌，
 *       存入 Redis（Key: {@code damai:idempotent:token:{token}} = userId，TTL 10min）。</li>
 *   <li>消费：用户提交下单时，调用 {@link #consumeToken(String, Long)} 原子消费令牌，
 *       使用 Redis Lua GETDEL 保证原子性（GET + DEL 在同一脚本中执行）。</li>
 *   <li>校验：令牌不存在或 userId 不匹配 → 拒绝下单（重复提交或令牌过期）。</li>
 * </ol>
 *
 * @author damai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentTokenService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成幂等令牌
     *
     * @param userId 用户ID
     * @return 幂等令牌（UUID）
     */
    public String generateToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = String.format(RedisKeyConstant.IDEMPOTENT_TOKEN, token);
        stringRedisTemplate.opsForValue().set(key, userId.toString(),
                RedisKeyConstant.TTL_IDEMPOTENT_TOKEN, TimeUnit.SECONDS);
        log.info("生成幂等令牌：userId={}, token={}", userId, token);
        return token;
    }

    /**
     * 原子消费幂等令牌（Lua GETDEL）
     *
     * <p>使用 Redis Lua 脚本原子执行：
     * <pre>
     *   local val = redis.call('GET', KEYS[1])
     *   if val then redis.call('DEL', KEYS[1]) end
     *   return val
     * </pre>
     *
     * @param token  幂等令牌
     * @param userId 用户ID（校验令牌归属）
     * @return true=消费成功（令牌有效），false=令牌无效或已使用
     */
    public boolean consumeToken(String token, Long userId) {
        String key = String.format(RedisKeyConstant.IDEMPOTENT_TOKEN, token);

        // Lua 脚本：原子 GET + DEL
        String luaScript = """
                local val = redis.call('GET', KEYS[1])
                if val then
                    redis.call('DEL', KEYS[1])
                end
                return val
                """;

        try {
            Object result = stringRedisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, String.class),
                    java.util.Collections.singletonList(key)
            );

            if (result == null) {
                log.warn("幂等令牌不存在或已消费：token={}", token);
                return false;
            }

            // 校验令牌归属
            if (!userId.toString().equals(result.toString())) {
                log.warn("幂等令牌归属不匹配：token={}, expectedUserId={}, actualUserId={}",
                        token, userId, result);
                return false;
            }

            log.info("幂等令牌消费成功：userId={}, token={}", userId, token);
            return true;
        } catch (Exception e) {
            log.error("幂等令牌消费异常：token={}", token, e);
            return false;
        }
    }
}
