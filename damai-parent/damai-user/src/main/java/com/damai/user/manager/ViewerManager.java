package com.damai.user.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.user.dao.ViewerMapper;
import com.damai.user.entity.Viewer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 观演人 Manager（缓存/DB 组合操作）
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewerManager {

    private final ViewerMapper viewerMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 观演人列表缓存前缀
     */
    private static final String VIEWER_LIST_CACHE_PREFIX = "user:viewers:";

    /**
     * 观演人列表缓存过期时间（秒）
     */
    private static final int VIEWER_LIST_CACHE_EXPIRE_SECONDS = 1800; // 30 分钟

    /**
     * 根据用户ID查询观演人列表（先走缓存，再走 DB）
     *
     * @param userId 用户ID
     * @return 观演人列表
     */
    public List<Viewer> listByUserId(Long userId) {
        // 1. 先查缓存
        String cacheKey = VIEWER_LIST_CACHE_PREFIX + userId;
        String cachedList = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedList != null) {
            if ("[]".equals(cachedList)) {
                return List.of();
            }
            return com.alibaba.fastjson2.JSON.parseArray(cachedList, Viewer.class);
        }

        // 2. 缓存未命中，查 DB
        LambdaQueryWrapper<Viewer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Viewer::getUserId, userId)
                .orderByDesc(Viewer::getIsDefault)
                .orderByDesc(Viewer::getCreateTime);
        List<Viewer> viewers = viewerMapper.selectList(wrapper);

        // 3. 写入缓存
        if (viewers != null && !viewers.isEmpty()) {
            stringRedisTemplate.opsForValue().set(cacheKey,
                    com.alibaba.fastjson2.JSON.toJSONString(viewers),
                    VIEWER_LIST_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } else {
            stringRedisTemplate.opsForValue().set(cacheKey, "[]", 60, TimeUnit.SECONDS);
        }

        return viewers;
    }

    /**
     * 根据观演人ID查询（校验归属）
     *
     * @param viewerId 观演人ID
     * @param userId   用户ID
     * @return 观演人信息（不属于该用户则返回 null）
     */
    public Viewer getByIdAndUserId(Long viewerId, Long userId) {
        LambdaQueryWrapper<Viewer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Viewer::getId, viewerId)
                .eq(Viewer::getUserId, userId);
        return viewerMapper.selectOne(wrapper);
    }

    /**
     * 新增观演人
     *
     * @param viewer 观演人实体
     * @return 影响行数
     */
    public int insert(Viewer viewer) {
        // 清除缓存
        evictViewerListCache(viewer.getUserId());
        return viewerMapper.insert(viewer);
    }

    /**
     * 更新观演人
     *
     * @param viewer 观演人实体
     * @return 影响行数
     */
    public int update(Viewer viewer) {
        // 清除缓存
        evictViewerListCache(viewer.getUserId());
        return viewerMapper.updateById(viewer);
    }

    /**
     * 删除观演人
     *
     * @param viewerId 观演人ID
     * @param userId   用户ID
     * @return 影响行数
     */
    public int delete(Long viewerId, Long userId) {
        // 清除缓存
        evictViewerListCache(userId);
        return viewerMapper.deleteById(viewerId);
    }

    /**
     * 统计用户观演人数量
     *
     * @param userId 用户ID
     * @return 观演人数量
     */
    public int countByUserId(Long userId) {
        LambdaQueryWrapper<Viewer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Viewer::getUserId, userId);
        return Math.toIntExact(viewerMapper.selectCount(wrapper));
    }

    /**
     * 清除观演人列表缓存
     *
     * @param userId 用户ID
     */
    private void evictViewerListCache(Long userId) {
        String cacheKey = VIEWER_LIST_CACHE_PREFIX + userId;
        stringRedisTemplate.delete(cacheKey);
    }
}
