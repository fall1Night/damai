package com.damai.starter.redis;

import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Redisson 自动配置。
 *
 * <p>基于 redisson-spring-boot-starter 自带的 {@link RedissonAutoConfigurationV2}，
 * 在此扩展布隆过滤器和分布式锁的辅助 Bean 注册。
 * 业务服务只需引入 damai-service-starter + 配置 redisson 即可自动生效。
 *
 * <p>配置来源：
 * <ul>
 *   <li>classpath 下的 {@code redisson.yml} 或 {@code redisson.yaml}（推荐，Nacos 下发）；</li>
 *   <li>或 {@code spring.data.redis.*} 前缀（Redisson Starter 自动适配）。</li>
 * </ul>
 *
 * @see BloomFilterHelper
 * @see DistributedLockHelper
 */
@AutoConfiguration(after = RedissonAutoConfigurationV2.class)
@ConditionalOnClass(RedissonClient.class)
public class RedissonAutoConfiguration {

    @Bean
    public BloomFilterHelper bloomFilterHelper(RedissonClient redissonClient) {
        return new BloomFilterHelper(redissonClient);
    }

    @Bean
    public DistributedLockHelper distributedLockHelper(RedissonClient redissonClient) {
        return new DistributedLockHelper(redissonClient);
    }
}
