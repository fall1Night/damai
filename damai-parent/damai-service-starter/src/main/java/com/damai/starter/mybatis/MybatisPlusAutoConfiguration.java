package com.damai.starter.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动配置 —— 对应技术文档 §6 四层分层 DAO 层。
 *
 * <p>职责：
 * <ul>
 *   <li>注册分页插件（{@link PaginationInnerInterceptor}，MySQL 方言）。</li>
 *   <li>注册自动填充处理器（{@link CreateUpdateMetaHandler}），
 *       自动填充 {@code create_time} / {@code update_time}。</li>
 *   <li>提供 {@code @MapperScan} 基础包扫描（各业务服务需按需覆盖）。</li>
 * </ul>
 *
 * <p>注意：ShardingSphere 分片数据源配置因每个服务的分片规则不同（order 按分片，
 * user/program 不分片），故不在此统一配置，由各服务自行在 config 包中定义。
 *
 * @see CreateUpdateMetaHandler
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.MybatisConfiguration")
public class MybatisPlusAutoConfiguration {

    /**
     * MyBatis-Plus 插件链（分页插件）。
     *
     * <p>后续可在此追加：乐观锁插件、防全表更新拦截器等。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件，指定 MySQL 方言
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器 —— 自动填充 create_time / update_time。
     *
     * <p>所有实体表的通用字段（DDL 统一定义）由此处理器自动维护，
     * 业务代码无需手动设置。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new CreateUpdateMetaHandler();
    }
}
