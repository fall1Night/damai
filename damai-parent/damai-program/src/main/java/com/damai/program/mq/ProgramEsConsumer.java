package com.damai.program.mq;

import com.damai.common.constants.KafkaTopicConstant;
import com.damai.program.entity.Program;
import com.damai.program.manager.ProgramManager;
import com.damai.starter.es.EsQueryHelper;
import com.damai.starter.kafka.BaseKafkaConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 节目 ES 同步消费者
 * <p>
 * 监听 damai-program-sync-es Topic，消费节目变更消息 → 更新 ES 索引
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramEsConsumer {

    private final ProgramManager programManager;
    private final EsQueryHelper esQueryHelper;

    /**
     * ES 索引名
     */
    private static final String ES_INDEX = "damai_program";

    /**
     * 消费节目同步消息
     *
     * @param record Kafka 消息
     */
    @KafkaListener(topics = KafkaTopicConstant.PROGRAM_SYNC_ES, groupId = "damai-program-es")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            String value = record.value();
            Map<String, Object> message = com.alibaba.fastjson2.JSON.parseObject(value, Map.class);
            Long programId = Long.valueOf(message.get("programId").toString());
            String action = message.get("action").toString();

            log.info("收到节目同步消息：programId={}, action={}", programId, action);

            switch (action) {
                case "SYNC" -> syncProgram(programId);
                case "DELETE" -> deleteProgram(programId);
                default -> log.warn("未知的同步动作：{}", action);
            }
        } catch (Exception e) {
            log.error("节目同步消息处理失败：{}", record.value(), e);
        }
    }

    /**
     * 同步节目到 ES
     *
     * @param programId 节目ID
     */
    private void syncProgram(Long programId) {
        Program program = programManager.getById(programId);
        if (program == null) {
            log.warn("节目不存在，跳过同步：programId={}", programId);
            return;
        }

        // 构建 ES 文档
        Map<String, Object> document = new HashMap<>();
        document.put("programId", program.getId().toString());
        document.put("title", program.getTitle());
        document.put("artist", program.getArtist());
        document.put("venue", program.getVenue());
        document.put("description", program.getDescription());
        document.put("typeCode", program.getTypeCode());
        document.put("cityCode", program.getCityCode());
        document.put("cityName", program.getCityName());
        document.put("status", program.getStatus().toString());
        document.put("priceMin", program.getPriceMin());
        document.put("priceMax", program.getPriceMax());
        document.put("showStart", program.getShowStart());
        document.put("showEnd", program.getShowEnd());
        document.put("saleStart", program.getSaleStart());
        document.put("heat", program.getHeat());
        document.put("posterUrl", program.getPosterUrl());
        document.put("createTime", program.getCreateTime());
        document.put("updateTime", program.getUpdateTime());

        // 写入 ES
        esQueryHelper.index(ES_INDEX, programId.toString(), document);
        log.info("节目同步到 ES 成功：programId={}", programId);
    }

    /**
     * 从 ES 删除节目
     *
     * @param programId 节目ID
     */
    private void deleteProgram(Long programId) {
        esQueryHelper.deleteById(ES_INDEX, programId.toString());
        log.info("节目从 ES 删除成功：programId={}", programId);
    }
}
