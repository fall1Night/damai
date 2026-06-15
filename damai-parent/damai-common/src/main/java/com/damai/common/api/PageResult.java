package com.damai.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页返回结果对象。
 *
 * <p>对应 PRD §11.3 分页约定：请求用 pageNum/pageSize，返回用 total + list。
 * 采用泛型 {@code <T>} 兼容任意业务实体列表。
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 当前页数据列表 */
    private List<T> list;

    /** 当前页码（从 1 开始） */
    private long pageNum;

    /** 每页大小 */
    private long pageSize;

    /**
     * 快速构造一个空结果（查询无数据时使用）。
     */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(0L, Collections.emptyList(), pageNum, pageSize);
    }

    /**
     * 快速构造一个分页结果。
     */
    public static <T> PageResult<T> of(long total, List<T> list, long pageNum, long pageSize) {
        return new PageResult<>(total, list, pageNum, pageSize);
    }
}
