package com.damai.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constants.RedisKeyConstant;
import com.damai.user.dao.UserMapper;
import com.damai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 用户 Manager（缓存/DB/中间件组合操作）
 * <p>
 * 职责：
 * 1. Redis 缓存用户信息
 * 2. 布隆过滤器校验手机号唯一性
 * 3. DB 读写
 * 4. 登录失败计数与锁定（Redis INCR + TTL）
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserManager {

    private final UserMapper userMapper;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 布隆过滤器名称（手机号唯一性）
     */
    private static final String BLOOM_FILTER_MOBILE = "bloom:user:mobile";

    /**
     * 登录失败次数前缀
     */
    private static final String LOGIN_FAIL_COUNT_PREFIX = "user:login:fail:";

    /**
     * 最大登录失败次数
     */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    /**
     * 登录锁定时间（秒）
     */
    private static final int LOGIN_LOCK_SECONDS = 900; // 15 分钟

    /**
     * 用户缓存前缀
     */
    private static final String USER_CACHE_PREFIX = "user:info:";

    /**
     * 用户缓存过期时间（秒）
     */
    private static final int USER_CACHE_EXPIRE_SECONDS = 1800; // 30 分钟

    /**
     * 根据手机号查询用户（先走缓存，再走 DB）
     *
     * @param mobile 手机号
     * @return 用户信息
     */
    public User getByMobile(String mobile) {
        // 1. 先查缓存
        String cacheKey = USER_CACHE_PREFIX + "mobile:" + mobile;
        String cachedUser = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedUser != null) {
            if ("NULL".equals(cachedUser)) {
                return null;
            }
            return com.alibaba.fastjson2.JSON.parseObject(cachedUser, User.class);
        }

        // 2. 缓存未命中，查 DB
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getMobile, mobile);
        User user = userMapper.selectOne(wrapper);

        // 3. 写入缓存（空值也缓存，防穿透）
        if (user != null) {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    com.alibaba.fastjson2.JSON.toJSONString(user),
                    USER_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "NULL", 60, TimeUnit.SECONDS);
        }

        return user;
    }

    /**
     * 根据用户ID查询用户（先走缓存，再走 DB）
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public User getById(Long userId) {
        // 1. 先查缓存
        String cacheKey = USER_CACHE_PREFIX + userId;
        String cachedUser = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedUser != null) {
            if ("NULL".equals(cachedUser)) {
                return null;
            }
            return com.alibaba.fastjson2.JSON.parseObject(cachedUser, User.class);
        }

        // 2. 缓存未命中，查 DB
        User user = userMapper.selectById(userId);

        // 3. 写入缓存
        if (user != null) {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    com.alibaba.fastjson2.JSON.toJSONString(user),
                    USER_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "NULL", 60, TimeUnit.SECONDS);
        }

        return user;
    }

    /**
     * 布隆过滤器：手机号是否已注册
     *
     * @param mobile 手机号
     * @return true=可能存在（已注册），false=一定不存在
     */
    public boolean mightExistByMobile(String mobile) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_MOBILE);
        return bloomFilter.contains(mobile);
    }

    /**
     * 布隆过滤器：添加手机号
     *
     * @param mobile 手机号
     */
    public void addMobileToBloomFilter(String mobile) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_MOBILE);
        // 预估元素数量 1000 万，误判率 0.01%
        bloomFilter.tryInit(10_000_000L, 0.0001);
        bloomFilter.add(mobile);
    }

    /**
     * 插入用户
     *
     * @param user 用户实体
     * @return 影响行数
     */
    public int insert(User user) {
        return userMapper.insert(user);
    }

    /**
     * 更新用户
     *
     * @param user 用户实体
     * @return 影响行数
     */
    public int update(User user) {
        // 清除缓存
        evictUserCache(user.getId());
        evictUserCacheByMobile(user.getMobile());
        return userMapper.updateById(user);
    }

    /**
     * 登录失败计数 + 1，返回当前失败次数
     *
     * @param mobile 手机号
     * @return 当前失败次数
     */
    public long incrLoginFailCount(String mobile) {
        String key = LOGIN_FAIL_COUNT_PREFIX + mobile;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 首次失败，设置过期时间
            stringRedisTemplate.expire(key, LOGIN_LOCK_SECONDS, TimeUnit.SECONDS);
        }
        return count != null ? count : 0;
    }

    /**
     * 获取登录失败次数
     *
     * @param mobile 手机号
     * @return 失败次数（0 表示未锁定或已过期）
     */
    public long getLoginFailCount(String mobile) {
        String key = LOGIN_FAIL_COUNT_PREFIX + mobile;
        String count = stringRedisTemplate.opsForValue().get(key);
        return count != null ? Long.parseLong(count) : 0;
    }

    /**
     * 清除登录失败计数
     *
     * @param mobile 手机号
     */
    public void clearLoginFailCount(String mobile) {
        String key = LOGIN_FAIL_COUNT_PREFIX + mobile;
        stringRedisTemplate.delete(key);
    }

    /**
     * 是否被锁定
     *
     * @param mobile 手机号
     * @return true=已锁定
     */
    public boolean isLoginLocked(String mobile) {
        return getLoginFailCount(mobile) >= MAX_LOGIN_FAIL_COUNT;
    }

    /**
     * 清除用户缓存
     *
     * @param userId 用户ID
     */
    private void evictUserCache(Long userId) {
        String cacheKey = USER_CACHE_PREFIX + userId;
        stringRedisTemplate.delete(cacheKey);
    }

    /**
     * 清除用户缓存（按手机号）
     *
     * @param mobile 手机号
     */
    private void evictUserCacheByMobile(String mobile) {
        String cacheKey = USER_CACHE_PREFIX + "mobile:" + mobile;
        stringRedisTemplate.delete(cacheKey);
    }
}
