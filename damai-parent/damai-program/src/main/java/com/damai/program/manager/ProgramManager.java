package com.damai.program.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constants.RedisKeyConstant;
import com.damai.program.dao.ProgramMapper;
import com.damai.program.entity.Program;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 节目 Manager（缓存/DB 组合操作）
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramManager {

    private final ProgramMapper programMapper;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 节目缓存前缀
     */
    private static final String PROGRAM_CACHE_PREFIX = "program:detail:";

    /**
     * 节目缓存过期时间（秒）- 30 分钟
     */
    private static final int PROGRAM_CACHE_EXPIRE_SECONDS = 1800;

    /**
     * 布隆过滤器名称（节目存在性）
     */
    private static final String BLOOM_FILTER_PROGRAM = "bloom:program:exists";

    /**
     * 根据节目ID查询（先走缓存，再走 DB）
     *
     * @param programId 节目ID
     * @return 节目信息
     */
    public Program getById(Long programId) {
        // 1. 先查缓存
        String cacheKey = PROGRAM_CACHE_PREFIX + programId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("NULL".equals(cached)) {
                return null;
            }
            return com.alibaba.fastjson2.JSON.parseObject(cached, Program.class);
        }

        // 2. 缓存未命中，查 DB
        Program program = programMapper.selectById(programId);

        // 3. 写入缓存
        if (program != null) {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    com.alibaba.fastjson2.JSON.toJSONString(program),
                    PROGRAM_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "NULL", 60, TimeUnit.SECONDS);
        }

        return program;
    }

    /**
     * 布隆过滤器：节目是否可能存在
     *
     * @param programId 节目ID
     * @return true=可能存在，false=一定不存在
     */
    public boolean mightExist(Long programId) {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PROGRAM);
        return bloomFilter.contains(programId);
    }

    /**
     * 布隆过滤器：添加节目ID
     *
     * @param programId 节目ID
     */
    public void addToBloomFilter(Long programId) {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PROGRAM);
        // 预估元素数量 100 万，误判率 0.01%
        bloomFilter.tryInit(1_000_000L, 0.0001);
        bloomFilter.add(programId);
    }

    /**
     * 插入节目
     *
     * @param program 节目实体
     * @return 影响行数
     */
    public int insert(Program program) {
        return programMapper.insert(program);
    }

    /**
     * 更新节目
     *
     * @param program 节目实体
     * @return 影响行数
     */
    public int update(Program program) {
        // 清除缓存
        evictCache(program.getId());
        return programMapper.updateById(program);
    }

    /**
     * 更新节目热度
     *
     * @param programId 节目ID
     * @param increment 增量
     */
    public void incrementHeat(Long programId, int increment) {
        Program program = getById(programId);
        if (program != null) {
            program.setHeat(program.getHeat() + increment);
            update(program);
        }
    }

    /**
     * 清除节目缓存
     *
     * @param programId 节目ID
     */
    public void evictCache(Long programId) {
        String cacheKey = PROGRAM_CACHE_PREFIX + programId;
        stringRedisTemplate.delete(cacheKey);
    }
}
