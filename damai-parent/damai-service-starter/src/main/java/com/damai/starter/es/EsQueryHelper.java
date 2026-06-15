package com.damai.starter.es;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ElasticSearch 通用查询封装 —— 对应技术文档 §7.3 节目索引查询。
 *
 * <p>职责：
 * <ul>
 *   <li>封装 ES Java API Client（Elasticsearch 8.x 风格）的常用查询构建；</li>
 *   <li>提供多条件筛选（BoolQuery）、分页、排序、高亮的模板方法；</li>
 *   <li>屏蔽底层 API 复杂度，业务 Manager 层直接调用。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   // 多条件筛选
 *   BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
 *   boolBuilder.filter(f -> f.term(t -> t.field("typeCode").value("concert")));
 *   boolBuilder.filter(f -> f.range(r -> r.field("priceMin").gte(co.doubleRange(cb -> cb.value(500.0)))));
 *
 *   PageResult&lt;ProgramDoc&gt; result = esQueryHelper.search("damai_program", boolBuilder.build(),
 *       ProgramDoc.class, 1, 10, "heat", Sort.Direction.DESC);
 * </pre>
 *
 * @see ElasticSearchAutoConfiguration
 */
@Slf4j
@Component
public class EsQueryHelper {

    private final ElasticsearchTemplate esTemplate;

    public EsQueryHelper(ElasticsearchTemplate esTemplate) {
        this.esTemplate = esTemplate;
    }

    /**
     * 通用 BoolQuery 条件筛选搜索（带分页、排序）。
     *
     * <p>适用于 PRD §7.2.2 分类浏览场景（按类型/城市/时间/价格组合筛选）。
     *
     * @param indexName ES 索引名（如 {@code damai_program}）
     * @param boolQuery BoolQuery 条件构建器
     * @param clazz    文档类（与 ES 索引 mapping 对应的 Java POJO）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页大小
     * @param sortField 排序字段
     * @param direction 排序方向
     * @param <T> 文档类型
     * @return 搜索结果（包含文档列表和总数）
     */
    public <T> EsSearchResult<T> search(String indexName, BoolQuery boolQuery, Class<T> clazz,
                                         int pageNum, int pageSize,
                                         String sortField, Sort.Direction direction) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQuery))
                .withPageable(PageRequest.of(pageNum - 1, pageSize, Sort.by(direction, sortField)))
                .build();

        SearchHits<T> searchHits = esTemplate.search(query, clazz);
        return buildResult(searchHits, pageNum, pageSize);
    }

    /**
     * 通用搜索（接受自定义 Query，用于 match/multi_match 等搜索场景）。
     *
     * <p>适用于 PRD §7.2.3 智能搜索（分词 match + 高亮 + 排序）。
     *
     * @param indexName ES 索引名
     * @param query     自定义 Query（如 multi_match）
     * @param clazz     文档类
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param <T> 文档类型
     * @return 搜索结果
     */
    public <T> EsSearchResult<T> search(String indexName, Query query, Class<T> clazz,
                                         int pageNum, int pageSize) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<T> searchHits = esTemplate.search(nativeQuery, clazz);
        return buildResult(searchHits, pageNum, pageSize);
    }

    /**
     * 按 ID 精确查询文档。
     *
     * @param indexName ES 索引名
     * @param id        文档 ID
     * @param clazz     文档类
     * @param <T> 文档类型
     * @return 文档对象，不存在返回 null
     */
    public <T> T getById(String indexName, String id, Class<T> clazz) {
        return esTemplate.get(id, clazz);
    }

    /**
     * 索引文档（新增或更新）。
     *
     * <p>用于节目发布/变更时同步到 ES（Kafka 消费 → ProgramEsManager → 此方法）。
     *
     * @param indexName ES 索引名
     * @param document  文档对象（需标注 @Document 或 @Id）
     * @param <T> 文档类型
     */
    public <T> void index(String indexName, T document) {
        esTemplate.save(document);
        log.debug("[ES] 文档已索引: index={}, docId={}", indexName,
                document instanceof org.springframework.data.elasticsearch.annotations.IdAware ? "unknown" : document);
    }

    /**
     * 按 ID 删除文档。
     *
     * <p>用于节目下架时从 ES 中移除。
     *
     * @param indexName ES 索引名
     * @param id        文档 ID
     */
    public void deleteById(String indexName, String id) {
        esTemplate.delete(id, Object.class);
        log.debug("[ES] 文档已删除: index={}, id={}", indexName, id);
    }

    /**
     * BoolQuery 构建器快捷工厂方法。
     *
     * <p>简化调用方的构建代码：
     * <pre>
     *   BoolQuery.Builder builder = EsQueryHelper.boolBuilder();
     *   builder.filter(f -> f.term(t -> t.field("typeCode").value("concert")));
     * </pre>
     *
     * @return BoolQuery.Builder 实例
     */
    public static BoolQuery.Builder boolBuilder() {
        return new BoolQuery.Builder();
    }

    /**
     * 构建搜索结果对象。
     */
    private <T> EsSearchResult<T> buildResult(SearchHits<T> searchHits, int pageNum, int pageSize) {
        List<T> list = new ArrayList<>();
        for (SearchHit<T> hit : searchHits.getSearchHits()) {
            list.add(hit.getContent());
        }
        return new EsSearchResult<>(searchHits.getTotalHits(), (long) pageNum, (long) pageSize, list);
    }
}
