package com.damai.program.service.impl;

import com.damai.common.api.PageResult;
import com.damai.common.constants.KafkaTopicConstant;
import com.damai.common.enums.ErrorCode;
import com.damai.common.exception.BizException;
import com.damai.program.entity.Program;
import com.damai.program.entity.Show;
import com.damai.program.entity.TicketType;
import com.damai.program.manager.ProgramManager;
import com.damai.program.manager.ShowManager;
import com.damai.program.manager.TicketTypeManager;
import com.damai.program.service.ProgramService;
import com.damai.program.stock.StockManager;
import com.damai.program.stock.StockPreheatService;
import com.damai.starter.es.EsQueryHelper;
import com.damai.starter.es.EsSearchResult;
import com.damai.starter.kafka.KafkaMessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 节目服务实现类
 *
 * @author damai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private final ProgramManager programManager;
    private final ShowManager showManager;
    private final TicketTypeManager ticketTypeManager;
    private final StockManager stockManager;
    private final StockPreheatService stockPreheatService;
    private final EsQueryHelper esQueryHelper;
    private final KafkaMessageHelper kafkaMessageHelper;

    private static final String ES_INDEX = "damai_program";

    @Override
    public List<Program> homeRecommend(String city, String channel) {
        // 从缓存获取首页推荐，或走 DB 查询热门节目
        return new ArrayList<>();
    }

    @Override
    public PageResult<Program> listByCategory(String typeCode, String cityCode, int pageNum, int pageSize) {
        // 构建 ES BoolQuery
        var boolQuery = esQueryHelper.boolBuilder();

        if (typeCode != null && !typeCode.isEmpty()) {
            boolQuery.filter(org.elasticsearch.index.query.QueryBuilders.termQuery("typeCode", typeCode));
        }
        if (cityCode != null && !cityCode.isEmpty()) {
            boolQuery.filter(org.elasticsearch.index.query.QueryBuilders.termQuery("cityCode", cityCode));
        }
        boolQuery.filter(org.elasticsearch.index.query.QueryBuilders.termQuery("status", "1"));

        // 执行搜索
        EsSearchResult<Map<String, Object>> result = esQueryHelper.search(boolQuery, pageNum, pageSize);

        // 转换为 Program 对象
        List<Program> programs = new ArrayList<>();
        for (Map<String, Object> item : result.getList()) {
            Program program = mapToProgram(item);
            if (program != null) {
                programs.add(program);
            }
        }

        return new PageResult<>(result.getTotal(), programs, pageNum, pageSize);
    }

    @Override
    public PageResult<Program> search(String keyword, int pageNum, int pageSize) {
        // 构建 multi_match 查询
        var multiMatchQuery = org.elasticsearch.index.query.QueryBuilders.multiMatchQuery(keyword,
                "title", "artist", "venue", "description");

        var boolQuery = esQueryHelper.boolBuilder()
                .must(multiMatchQuery)
                .filter(org.elasticsearch.index.query.QueryBuilders.termQuery("status", "1"));

        // 执行搜索
        EsSearchResult<Map<String, Object>> result = esQueryHelper.search(boolQuery, pageNum, pageSize);

        // 转换为 Program 对象
        List<Program> programs = new ArrayList<>();
        for (Map<String, Object> item : result.getList()) {
            Program program = mapToProgram(item);
            if (program != null) {
                programs.add(program);
            }
        }

        return new PageResult<>(result.getTotal(), programs, pageNum, pageSize);
    }

    @Override
    public Map<String, Object> detail(Long programId) {
        // 1. 布隆过滤器快速判断
        if (!programManager.mightExist(programId)) {
            throw new BizException(ErrorCode.PROGRAM_NOT_FOUND);
        }

        // 2. 查询节目信息
        Program program = programManager.getById(programId);
        if (program == null) {
            throw new BizException(ErrorCode.PROGRAM_NOT_FOUND);
        }

        // 3. 查询场次列表
        List<Show> shows = showManager.listByProgramId(programId);

        // 4. 查询每个场次的票档和库存
        List<Map<String, Object>> showDetails = new ArrayList<>();
        for (Show show : shows) {
            Map<String, Object> showDetail = new HashMap<>();
            showDetail.put("show", show);

            List<TicketType> ticketTypes = ticketTypeManager.listByShowId(show.getId());
            List<Map<String, Object>> ticketTypeDetails = new ArrayList<>();
            for (TicketType ticketType : ticketTypes) {
                Map<String, Object> ttDetail = new HashMap<>();
                ttDetail.put("ticketType", ticketType);
                Long stock = stockManager.getStock(show.getId(), ticketType.getId());
                ttDetail.put("stock", stock != null ? stock : ticketType.getSaleStock());
                ticketTypeDetails.add(ttDetail);
            }
            showDetail.put("ticketTypes", ticketTypeDetails);
            showDetails.add(showDetail);
        }

        // 5. 增加热度
        programManager.incrementHeat(programId, 1);

        // 6. 组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("program", program);
        result.put("shows", showDetails);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Program program) {
        program.setStatus(0);
        program.setHeat(0);
        program.setDeleted(0);
        program.setCreateTime(LocalDateTime.now());
        program.setUpdateTime(LocalDateTime.now());
        programManager.insert(program);
        stockPreheatService.preheatProgram(program.getId());
        log.info("节目发布成功：programId={}", program.getId());
        return program.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onShelf(Long programId) {
        Program program = programManager.getById(programId);
        if (program == null) {
            throw new BizException(ErrorCode.PROGRAM_NOT_FOUND);
        }
        program.setStatus(1);
        program.setUpdateTime(LocalDateTime.now());
        programManager.update(program);
        syncToEs(programId);
        log.info("节目上架成功：programId={}", programId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offShelf(Long programId) {
        Program program = programManager.getById(programId);
        if (program == null) {
            throw new BizException(ErrorCode.PROGRAM_NOT_FOUND);
        }
        program.setStatus(2);
        program.setUpdateTime(LocalDateTime.now());
        programManager.update(program);
        syncToEs(programId);
        log.info("节目下架成功：programId={}", programId);
    }

    @Override
    public List<Show> listShows(Long programId) {
        return showManager.listByProgramId(programId);
    }

    @Override
    public List<Map<String, Object>> listTicketTypes(Long showId) {
        List<TicketType> ticketTypes = ticketTypeManager.listByShowId(showId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TicketType ticketType : ticketTypes) {
            Map<String, Object> item = new HashMap<>();
            item.put("ticketType", ticketType);
            Long stock = stockManager.getStock(showId, ticketType.getId());
            item.put("stock", stock != null ? stock : ticketType.getSaleStock());
            result.add(item);
        }
        return result;
    }

    private void syncToEs(Long programId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("programId", programId);
            message.put("action", "SYNC");
            kafkaMessageHelper.sendAsync(KafkaTopicConstant.PROGRAM_SYNC_ES, message);
        } catch (Exception e) {
            log.error("节目同步 ES 消息发送失败：programId={}", programId, e);
        }
    }

    private Program mapToProgram(Map<String, Object> map) {
        try {
            Program program = new Program();
            program.setId(Long.valueOf(map.get("programId").toString()));
            program.setTitle((String) map.get("title"));
            program.setArtist((String) map.get("artist"));
            program.setVenue((String) map.get("venue"));
            program.setDescription((String) map.get("description"));
            program.setTypeCode((String) map.get("typeCode"));
            program.setCityCode((String) map.get("cityCode"));
            program.setCityName((String) map.get("cityName"));
            program.setStatus(Integer.valueOf(map.get("status").toString()));
            if (map.get("priceMin") != null) {
                program.setPriceMin(new java.math.BigDecimal(map.get("priceMin").toString()));
            }
            if (map.get("priceMax") != null) {
                program.setPriceMax(new java.math.BigDecimal(map.get("priceMax").toString()));
            }
            program.setHeat(map.get("heat") != null ? Integer.valueOf(map.get("heat").toString()) : 0);
            program.setPosterUrl((String) map.get("posterUrl"));
            return program;
        } catch (Exception e) {
            log.warn("ES 文档转换失败", e);
            return null;
        }
    }
}
