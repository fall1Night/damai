package com.damai.program.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.program.dao.ShowMapper;
import com.damai.program.entity.Show;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 场次 Manager（缓存/DB 组合操作）
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShowManager {

    private final ShowMapper showMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 场次列表缓存前缀
     */
    private static final String SHOW_LIST_CACHE_PREFIX = "program:shows:";

    /**
     * 场次列表缓存过期时间（秒）- 30 分钟
     */
    private static final int SHOW_LIST_CACHE_EXPIRE_SECONDS = 1800;

    /**
     * 根据节目ID查询场次列表（先走缓存，再走 DB）
     *
     * @param programId 节目ID
     * @return 场次列表
     */
    public List<Show> listByProgramId(Long programId) {
        // 1. 先查缓存
        String cacheKey = SHOW_LIST_CACHE_PREFIX + programId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("[]".equals(cached)) {
                return List.of();
            }
            return com.alibaba.fastjson2.JSON.parseArray(cached, Show.class);
        }

        // 2. 缓存未命中，查 DB
        LambdaQueryWrapper<Show> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Show::getProgramId, programId)
                .orderByAsc(Show::getShowTime);
        List<Show> shows = showMapper.selectList(wrapper);

        // 3. 写入缓存
        if (shows != null && !shows.isEmpty()) {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    com.alibaba.fastjson2.JSON.toJSONString(shows),
                    SHOW_LIST_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "[]", 60, TimeUnit.SECONDS);
        }

        return shows;
    }

    /**
     * 根据场次ID查询
     *
     * @param showId 场次ID
     * @return 场次信息
     */
    public Show getById(Long showId) {
        return showMapper.selectById(showId);
    }

    /**
     * 插入场次
     *
     * @param show 场次实体
     * @return 影响行数
     */
    public int insert(Show show) {
        evictListCache(show.getProgramId());
        return showMapper.insert(show);
    }

    /**
     * 更新场次
     *
     * @param show 场次实体
     * @return 影响行数
     */
    public int update(Show show) {
        evictListCache(show.getProgramId());
        return showMapper.updateById(show);
    }

    /**
     * 清除场次列表缓存
     *
     * @param programId 节目ID
     */
    public void evictListCache(Long programId) {
        String cacheKey = SHOW_LIST_CACHE_PREFIX + programId;
        stringRedisTemplate.delete(cacheKey);
    }
}
