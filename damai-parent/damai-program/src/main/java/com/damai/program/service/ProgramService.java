package com.damai.program.service;

import com.damai.common.api.PageResult;
import com.damai.program.entity.Program;
import com.damai.program.entity.Show;
import com.damai.program.entity.TicketType;

import java.util.List;
import java.util.Map;

/**
 * 节目服务接口
 *
 * @author damai
 */
public interface ProgramService {

    /**
     * 首页推荐
     *
     * @param city    城市编码
     * @param channel 频道（节目类型编码）
     * @return 推荐节目列表
     */
    List<Program> homeRecommend(String city, String channel);

    /**
     * 分类浏览（走 ES 多维筛选）
     *
     * @param typeCode    节目类型编码
     * @param cityCode    城市编码
     * @param pageNum     页码
     * @param pageSize    每页数量
     * @return 分页结果
     */
    PageResult<Program> listByCategory(String typeCode, String cityCode, int pageNum, int pageSize);

    /**
     * 智能搜索（走 ES 分词搜索）
     *
     * @param keyword  关键词
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    PageResult<Program> search(String keyword, int pageNum, int pageSize);

    /**
     * 节目详情（聚合场次+票档+实时库存）
     *
     * @param programId 节目ID
     * @return 包含场次、票档、库存的详情
     */
    Map<String, Object> detail(Long programId);

    /**
     * 节目发布（落库 → 触发预热 → 发 Kafka 同步 ES → 布隆写入）
     *
     * @param program 节目信息
     * @return 节目ID
     */
    Long publish(Program program);

    /**
     * 上架
     *
     * @param programId 节目ID
     */
    void onShelf(Long programId);

    /**
     * 下架
     *
     * @param programId 节目ID
     */
    void offShelf(Long programId);

    /**
     * 查询场次列表
     *
     * @param programId 节目ID
     * @return 场次列表
     */
    List<Show> listShows(Long programId);

    /**
     * 查询票档列表（含实时库存）
     *
     * @param showId 场次ID
     * @return 票档列表（含库存）
     */
    List<Map<String, Object>> listTicketTypes(Long showId);
}
