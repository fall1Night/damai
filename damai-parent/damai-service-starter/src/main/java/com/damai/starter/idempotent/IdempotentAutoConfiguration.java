package com.damai.starter.idempotent;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 幂等组件自动配置 —— 对应技术文档 §10.1 / §10.2 幂等与防重设计。
 *
 * <p>设计要点：
 * <ul>
 *   <li>starter 是独立 jar，业务服务的 {@code @SpringBootApplication} 默认只扫描自身包
 *       （如 {@code com.damai.user}），扫不到 {@code com.damai.starter.idempotent} 包。</li>
 *   <li>因此切面 {@link IdempotentTokenAspect} / {@link PreventDuplicateAspect} 不使用
 *       {@code @Component}，而是由本配置类集中声明为 Bean，并通过
 *       {@code AutoConfiguration.imports} 注册。</li>
 *   <li>仅当 classpath 存在 RedissonClient 与 Spring AOP 时才装配，避免无缓存服务也强依赖。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
public class IdempotentAutoConfiguration {

    /**
     * 幂等令牌切面 —— 标注 {@link IdempotentToken} 的方法会先消费令牌再执行。
     * <p>Order = HIGHEST_PRECEDENCE + 1，先于防重锁切面执行。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public IdempotentTokenAspect idempotentTokenAspect(RedissonClient redissonClient) {
        return new IdempotentTokenAspect(redissonClient);
    }

    /**
     * 防重锁切面 —— 标注 {@link PreventDuplicate} 的方法会先尝试短窗口分布式锁。
     * <p>Order = HIGHEST_PRECEDENCE + 2，后于幂等令牌切面执行。
     */
    @Bean
    @ConditionalOnBean(RedissonClient.class)
    public PreventDuplicateAspect preventDuplicateAspect(RedissonClient redissonClient) {
        return new PreventDuplicateAspect(redissonClient);
    }
}
