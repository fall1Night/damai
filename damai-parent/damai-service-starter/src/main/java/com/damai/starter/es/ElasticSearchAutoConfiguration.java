package com.damai.starter.es;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * ElasticSearch 自动配置。
 *
 * <p>Spring Boot 4.x 中 ES 客户端通过 spring-boot-starter-data-elasticsearch 自动配置，
 * 此处扩展注册通用查询封装 {@link EsQueryHelper}。
 *
 * <p>索引名约定见 {@code damai-parent/sql/es/program-mapping.json}（索引名 {@code damai_program}）。
 * 字段与 MySQL t_program 表通过 Kafka 异步同步（技术文档 §7.3）。
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate")
@ConditionalOnProperty(prefix = "spring.elasticsearch", name = "uris", matchIfMissing = false)
public class ElasticSearchAutoConfiguration {

    @Bean
    public EsQueryHelper esQueryHelper(
            org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate esTemplate) {
        return new EsQueryHelper(esTemplate);
    }
}
