package com.damai.starter.redis;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁工具封装 —— 对应技术文档 §8.5 Redisson / §9.2 L2 串行化。
 *
 * <p>核心特性：
 * <ul>
 *   <li>基于 Redisson {@link RLock}，默认启用看门狗（watchdog）自动续期，
 *       防止业务未执行完锁就过期。</li>
 *   <li>提供带等待超时的 {@link #tryLockWithTimeout} 和无等待的 {@link #tryLock}。</li>
 *   <li>提供带业务回退的 {@link #executeWithLock}，自动获取锁、执行业务、释放锁。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   // 方式一：手动获取与释放
 *   String lockKey = RedisKeyConstant.STOCK_LOCK.formatted(showId, ticketTypeId);
 *   if (lockHelper.tryLock(lockKey)) {
 *       try { ... 业务逻辑 ... } finally { lockHelper.unlock(lockKey); }
 *   }
 *
 *   // 方式二：自动管理（推荐）
 *   OrderResult result = lockHelper.executeWithLock(lockKey, 5, TimeUnit.SECONDS,
 *       () -> orderService.doCreateOrder(request));
 * </pre>
 *
 * @see RedissonAutoConfiguration
 */
@Slf4j
public class DistributedLockHelper {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 尝试获取锁（不等待，获取失败立即返回 false）。
     *
     * @param lockKey 锁的 Redis Key
     * @return true=获取成功；false=锁已被其他线程持有
     */
    public boolean tryLock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // leaseTime=-1 表示启用看门狗自动续期
            return lock.tryLock(0, -1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Lock] 获取锁被中断: key={}", lockKey);
            return false;
        }
    }

    /**
     * 尝试获取锁（带等待超时）。
     *
     * @param lockKey 锁的 Redis Key
     * @param waitTime 等待获取锁的最大时间
     * @param unit 时间单位
     * @return true=获取成功；false=超时未获取到
     */
    public boolean tryLockWithTimeout(String lockKey, long waitTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // leaseTime=-1 表示启用看门狗自动续期
            return lock.tryLock(waitTime, -1, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Lock] 获取锁超时被中断: key={}, waitTime={}", lockKey, waitTime);
            return false;
        }
    }

    /**
     * 尝试获取锁（带等待超时和自定义锁持有时间）。
     *
     * <p>适用于不需要看门狗续期、需要精确控制锁持有时间的场景。
     *
     * @param lockKey 锁的 Redis Key
     * @param waitTime 等待获取锁的最大时间
     * @param leaseTime 锁的持有时间（到期自动释放）
     * @param unit 时间单位
     * @return true=获取成功；false=超时未获取到
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Lock] 获取锁超时被中断: key={}, waitTime={}, leaseTime={}", lockKey, waitTime, leaseTime);
            return false;
        }
    }

    /**
     * 释放锁（仅释放当前线程持有的锁，防止误释放）。
     *
     * @param lockKey 锁的 Redis Key
     */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        // isHeldByCurrentThread：确保只释放自己持有的锁
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("[Lock] 释放锁成功: key={}", lockKey);
        }
    }

    /**
     * 带业务回退的分布式锁执行模板（推荐使用）。
     *
     * <p>自动完成：获取锁 → 执行业务 → 释放锁（finally 保证）。
     * 获取失败时返回 fallback 值。
     *
     * @param lockKey 锁的 Redis Key
     * @param waitTime 等待获取锁的最大时间
     * @param unit 时间单位
     * @param supplier 业务逻辑（获取锁成功后执行）
     * @param fallback 获取锁失败时的回退值
     * @param <T> 返回值类型
     * @return 业务执行结果或 fallback
     */
    public <T> T executeWithLock(String lockKey, long waitTime, TimeUnit unit,
                                 Supplier<T> supplier, T fallback) {
        if (tryLockWithTimeout(lockKey, waitTime, unit)) {
            try {
                return supplier.get();
            } finally {
                unlock(lockKey);
            }
        }
        log.info("[Lock] 获取锁失败，使用 fallback: key={}", lockKey);
        return fallback;
    }

    /**
     * 带业务回退的分布式锁执行模板（无 fallback，获取失败返回 null）。
     *
     * @param lockKey 锁的 Redis Key
     * @param waitTime 等待获取锁的最大时间
     * @param unit 时间单位
     * @param supplier 业务逻辑
     * @param <T> 返回值类型
     * @return 业务执行结果，获取锁失败返回 null
     */
    public <T> T executeWithLock(String lockKey, long waitTime, TimeUnit unit,
                                 Supplier<T> supplier) {
        return executeWithLock(lockKey, waitTime, unit, supplier, null);
    }
}
