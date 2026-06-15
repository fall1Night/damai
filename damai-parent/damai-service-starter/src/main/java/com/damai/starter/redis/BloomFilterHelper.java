package com.damai.starter.redis;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 布隆过滤器通用工具 —— 对应技术文档 §9.3.1 防穿透。
 *
 * <p>使用场景（对应 {@link com.damai.common.constants.RedisKeyConstant}）：
 * <ul>
 *   <li>{@code BLOOM_PROGRAM_EXISTS}：节目存在性预判，查询前先过布隆，不存在的 ID 直接返回。</li>
 *   <li>{@code BLOOM_USER_MOBILE}：手机号唯一性预判。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   // 初始化（服务启动 / 预热时调用一次）
 *   bloomFilterHelper.tryInit("damai:bloom:program:exists", 100_000L, 0.01);
 *
 *   // 写入
 *   bloomFilterHelper.add("damai:bloom:program:exists", programId.toString());
 *
 *   // 判断
 *   boolean mightExist = bloomFilterHelper.mightContain("damai:bloom:program:exists", programId.toString());
 *   if (!mightExist) {
 *       // 一定不存在，直接返回，不打 DB
 *   }
 * </pre>
 *
 * @see RedissonAutoConfiguration
 */
@Slf4j
public class BloomFilterHelper {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 初始化布隆过滤器（幂等，重复调用不报错）。
     *
     * @param filterName 过滤器名称（即 Redis Key）
     * @param expectedInsertions 预期插入量（决定位数组大小）
     * @param falseProbability 误判率（越低占用空间越大）
     * @param <T> 元素类型
     * @return 布隆过滤器实例
     */
    public <T> RBloomFilter<T> tryInit(String filterName, long expectedInsertions, double falseProbability) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(filterName);
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(expectedInsertions, falseProbability);
            log.info("[BloomFilter] 初始化完成: name={}, expectedInsertions={}, falseProbability={}",
                    filterName, expectedInsertions, falseProbability);
        }
        return bloomFilter;
    }

    /**
     * 向布隆过滤器添加元素。
     *
     * @param filterName 过滤器名称
     * @param value 待添加元素
     * @param <T> 元素类型
     * @return true=首次添加成功；false=可能已存在（布隆特性，不精确）
     */
    public <T> boolean add(String filterName, T value) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(filterName);
        return bloomFilter.add(value);
    }

    /**
     * 判断元素是否可能存在于布隆过滤器。
     *
     * <p><b>返回 true ≠ 一定存在</b>，只是"可能存在"（有误判率）。
     * <br><b>返回 false = 一定不存在</b>，可用于防穿透直接拦截。
     *
     * @param filterName 过滤器名称
     * @param value 待判断元素
     * @param <T> 元素类型
     * @return true=可能存在；false=一定不存在
     */
    public <T> boolean mightContain(String filterName, T value) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(filterName);
        return bloomFilter.contains(value);
    }

    /**
     * 获取布隆过滤器实例（供高级操作使用）。
     *
     * @param filterName 过滤器名称
     * @param <T> 元素类型
     * @return 布隆过滤器实例
     */
    public <T> RBloomFilter<T> getFilter(String filterName) {
        return redissonClient.getBloomFilter(filterName);
    }
}
