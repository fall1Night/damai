package com.damai.starter.es;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ElasticSearch 通用搜索结果封装。
 *
 * <p>与 {@link com.damai.common.api.PageResult} 不同：
 * <ul>
 *   <li>PageResult 用于 Controller 层返回给前端；</li>
 *   <li>EsSearchResult 用于 Manager 层 ES 查询的内部传递。</li>
 * </ul>
 *
 * <p>Manager 层拿到 EsSearchResult 后，需转换为业务 DTO 或 PageResult 返回给 Service。
 *
 * @param <T> 文档类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EsSearchResult<T> {

    /** 总命中数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long pageNum;

    /** 每页大小 */
    private long pageSize;

    /** 当前页文档列表 */
    private List<T> list;

    /**
     * 计算总页数。
     */
    public long getTotalPages() {
        if (pageSize <= 0) return 0;
        return (total + pageSize - 1) / pageSize;
    }

    /**
     * 判断是否有下一页。
     */
    public boolean hasNext() {
        return pageNum < getTotalPages();
    }
}
