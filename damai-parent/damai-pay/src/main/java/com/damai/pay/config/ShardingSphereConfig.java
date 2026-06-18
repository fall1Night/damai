package com.damai.pay.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * ShardingSphere 分片规则配置 —— 与 {@code damai-order} 同分片组。
 *
 * <p>对应技术文档 §7.1.2 分库分表策略。分片规则：
 * <ul>
 *   <li>逻辑表：t_order / t_order_detail / <b>t_pay</b></li>
 *   <li>实际数据源：ds_0 ~ ds_3（4 库）</li>
 *   <li>每个库内物理表：t_order_0 ~ t_order_7（8 表）</li>
 *   <li>分片键：user_id（t_pay.user_id 为冗余分片键，保证与 t_order 同分片路由）</li>
 *   <li>路由算法：user_id % 32 → 确定库和表（库号=user_id%32/8，表号=user_id%32%8）</li>
 * </ul>
 *
 * <p>支付单 {@code t_pay} 通过 {@code order_id}（雪花算法低位嵌入 user_id）与 {@code user_id}
 * 双重保证落在与订单相同的分片，避免跨库关联。
 *
 * <p>开发环境简化：单库单表，分片规则通过 Nacos 配置中心切换。
 * 生产环境配置示例见 bootstrap.yml 注释。
 *
 * @author damai
 */
@Slf4j
@Configuration
public class ShardingSphereConfig {

    /**
     * 分片数量常量
     */
    public static final int SHARDING_COUNT = 32;

    /**
     * 数据源数量
     */
    public static final int DATASOURCE_COUNT = 4;

    /**
     * 每个数据源内的表数量
     */
    public static final int TABLES_PER_DATASOURCE = 8;

    /**
     * 根据 user_id 计算数据源编号。
     *
     * @param userId 用户ID
     * @return 数据源编号（0 ~ 3）
     */
    public static int getDataSourceIndex(Long userId) {
        return (int) (userId % SHARDING_COUNT / TABLES_PER_DATASOURCE);
    }

    /**
     * 根据 user_id 计算表编号。
     *
     * @param userId 用户ID
     * @return 表编号（0 ~ 7）
     */
    public static int getTableIndex(Long userId) {
        return (int) (userId % SHARDING_COUNT % TABLES_PER_DATASOURCE);
    }

    /**
     * 生成嵌入 user_id 分片信息的雪花 ID 低位掩码。
     * <p>实际雪花 ID 生成由业务层负责，此处仅提供掩码计算工具。
     *
     * @param userId 用户ID
     * @return 分片掩码（0 ~ 31）
     */
    public static long getShardingMask(Long userId) {
        return userId % SHARDING_COUNT;
    }
}
