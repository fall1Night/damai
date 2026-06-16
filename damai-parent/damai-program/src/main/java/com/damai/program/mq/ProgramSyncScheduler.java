package com.damai.program.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constants.KafkaTopicConstant;
import com.damai.program.dao.ProgramMapper;
import com.damai.program.entity.Program;
import com.damai.starter.kafka.KafkaMessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节目同步定时校对任务
 * <p>
 * 每小时全量比对 MySQL 与 ES 数据，兜底消息丢失导致的不一致
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramSyncScheduler {

    private final ProgramMapper programMapper;
    private final KafkaMessageHelper kafkaMessageHelper;

    /**
     * 每小时执行一次全量校对
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void syncAllPrograms() {
        log.info("开始节目全量校对任务...");

        try {
            // 查询所有未删除的节目
            LambdaQueryWrapper<Program> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Program::getDeleted, 0);
            List<Program> programs = programMapper.selectList(wrapper);

            int syncCount = 0;
            for (Program program : programs) {
                try {
                    // 发送同步消息
                    Map<String, Object> message = new HashMap<>();
                    message.put("programId", program.getId());
                    message.put("action", "SYNC");
                    kafkaMessageHelper.sendAsync(KafkaTopicConstant.PROGRAM_SYNC_ES, message);
                    syncCount++;
                } catch (Exception e) {
                    log.error("节目同步消息发送失败：programId={}", program.getId(), e);
                }
            }

            log.info("节目全量校对任务完成：总数={}, 已同步={}", programs.size(), syncCount);
        } catch (Exception e) {
            log.error("节目全量校对任务异常", e);
        }
    }
}
