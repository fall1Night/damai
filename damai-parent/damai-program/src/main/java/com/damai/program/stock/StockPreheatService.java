package com.damai.program.stock;

import com.damai.common.constants.KafkaTopicConstant;
import com.damai.common.constants.RedisKeyConstant;
import com.damai.program.entity.Program;
import com.damai.program.entity.Show;
import com.damai.program.entity.TicketType;
import com.damai.program.manager.ProgramManager;
import com.damai.program.manager.ShowManager;
import com.damai.program.manager.TicketTypeManager;
import com.damai.starter.kafka.KafkaMessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存预热服务
 * <p>
 * 职责：
 * 1. DB 库存 → Redis 写入（初始化 stock:{showId}:{ticketTypeId} Key）
 * 2. 节目信息写 ES（发 Kafka 异步同步）
 * 3. 节目 ID 写布隆过滤器
 *
 * @author damai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPreheatService {

    private final StockManager stockManager;
    private final ProgramManager programManager;
    private final ShowManager showManager;
    private final TicketTypeManager ticketTypeManager;
    private final KafkaMessageHelper kafkaMessageHelper;

    /**
     * 预热指定节目的所有库存
     *
     * @param programId 节目ID
     */
    public void preheatProgram(Long programId) {
        log.info("开始预热节目库存：programId={}", programId);

        // 1. 校验节目是否存在
        Program program = programManager.getById(programId);
        if (program == null) {
            log.warn("节目不存在，跳过预热：programId={}", programId);
            return;
        }

        // 2. 查询所有场次
        List<Show> shows = showManager.listByProgramId(programId);
        if (shows.isEmpty()) {
            log.warn("节目无场次，跳过预热：programId={}", programId);
            return;
        }

        // 3. 遍历场次，预热每个票档的库存
        int totalPreheated = 0;
        for (Show show : shows) {
            List<TicketType> ticketTypes = ticketTypeManager.listByShowId(show.getId());
            for (TicketType ticketType : ticketTypes) {
                // 初始化 Redis 库存
                stockManager.initStock(show.getId(), ticketType.getId(), ticketType.getSaleStock());
                totalPreheated++;
            }
        }

        // 4. 节目 ID 写入布隆过滤器
        programManager.addToBloomFilter(programId);

        // 5. 发送 Kafka 消息，异步同步到 ES
        syncToEs(programId);

        log.info("节目库存预热完成：programId={}, 预热票档数={}", programId, totalPreheated);
    }

    /**
     * 同步节目信息到 ES
     *
     * @param programId 节目ID
     */
    private void syncToEs(Long programId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("programId", programId);
            message.put("action", "SYNC");
            kafkaMessageHelper.sendAsync(KafkaTopicConstant.PROGRAM_SYNC_ES, message);
            log.debug("节目同步 ES 消息已发送：programId={}", programId);
        } catch (Exception e) {
            log.error("节目同步 ES 消息发送失败：programId={}", programId, e);
        }
    }

    /**
     * 批量预热（管理端调用）
     *
     * @param programIds 节目ID列表
     */
    public void batchPreheat(List<Long> programIds) {
        log.info("开始批量预热节目库存：数量={}", programIds.size());
        for (Long programId : programIds) {
            try {
                preheatProgram(programId);
            } catch (Exception e) {
                log.error("节目预热失败：programId={}", programId, e);
            }
        }
        log.info("批量预热完成");
    }
}
