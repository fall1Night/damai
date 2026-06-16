package com.damai.program.stock;

import com.damai.common.constants.RedisKeyConstant;
import com.damai.program.entity.TicketType;
import com.damai.program.manager.TicketTypeManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 库存 Manager（Redis Lua 预扣 + DB 乐观锁兜底）
 *
 * @author damai
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockManager {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final TicketTypeManager ticketTypeManager;

    /**
     * Lua 脚本 SHA（预扣）
     */
    private String deductScriptSha;

    /**
     * Lua 脚本 SHA（归还）
     */
    private String refundScriptSha;

    /**
     * 加载 Lua 脚本
     */
    @PostConstruct
    public void loadScripts() {
        try {
            // 加载预扣脚本
            String deductScript = loadLuaScript("lua/stock_deduct.lua");
            deductScriptSha = redissonClient.getScript().scriptLoad(deductScript);
            log.info("库存预扣 Lua 脚本加载成功，SHA: {}", deductScriptSha);

            // 加载归还脚本
            String refundScript = loadLuaScript("lua/stock_refund.lua");
            refundScriptSha = redissonClient.getScript().scriptLoad(refundScript);
            log.info("库存归还 Lua 脚本加载成功，SHA: {}", refundScriptSha);
        } catch (Exception e) {
            log.error("Lua 脚本加载失败", e);
        }
    }

    /**
     * 读取 Lua 脚本文件
     */
    private String loadLuaScript(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取库存 Key
     */
    private String getStockKey(Long showId, Long ticketTypeId) {
        return String.format(RedisKeyConstant.STOCK, showId, ticketTypeId);
    }

    /**
     * 预扣库存（Redis Lua 原子操作）
     *
     * @param showId       场次ID
     * @param ticketTypeId 票档ID
     * @param quantity     扣减数量
     * @return 1=成功, -1=库存不足, -2=Key不存在
     */
    public Long deductStock(Long showId, Long ticketTypeId, int quantity) {
        String stockKey = getStockKey(showId, ticketTypeId);
        try {
            RScript script = redissonClient.getScript();
            Long result = script.evalSha(
                    RScript.Mode.READ_WRITE,
                    deductScriptSha,
                    RScript.ReturnType.INTEGER,
                    Collections.singletonList(stockKey),
                    quantity
            );
            log.debug("库存预扣结果：showId={}, ticketTypeId={}, quantity={}, result={}",
                    showId, ticketTypeId, quantity, result);
            return result;
        } catch (RedisException e) {
            log.error("Redis 库存预扣异常：showId={}, ticketTypeId={}", showId, ticketTypeId, e);
            return -2L;
        }
    }

    /**
     * 归还库存（Redis Lua 原子操作）
     *
     * @param showId       场次ID
     * @param ticketTypeId 票档ID
     * @param quantity     归还数量
     * @return 1=成功, -2=Key不存在
     */
    public Long refundStock(Long showId, Long ticketTypeId, int quantity) {
        String stockKey = getStockKey(showId, ticketTypeId);
        try {
            RScript script = redissonClient.getScript();
            Long result = script.evalSha(
                    RScript.Mode.READ_WRITE,
                    refundScriptSha,
                    RScript.ReturnType.INTEGER,
                    Collections.singletonList(stockKey),
                    quantity
            );
            log.debug("库存归还结果：showId={}, ticketTypeId={}, quantity={}, result={}",
                    showId, ticketTypeId, quantity, result);
            return result;
        } catch (RedisException e) {
            log.error("Redis 库存归还异常：showId={}, ticketTypeId={}", showId, ticketTypeId, e);
            return -2L;
        }
    }

    /**
     * 获取当前库存
     *
     * @param showId       场次ID
     * @param ticketTypeId 票档ID
     * @return 库存数量（Key 不存在返回 null）
     */
    public Long getStock(Long showId, Long ticketTypeId) {
        String stockKey = getStockKey(showId, ticketTypeId);
        String stock = stringRedisTemplate.opsForValue().get(stockKey);
        return stock != null ? Long.parseLong(stock) : null;
    }

    /**
     * 初始化库存（预热）
     *
     * @param showId       场次ID
     * @param ticketTypeId 票档ID
     * @param stock        初始库存
     */
    public void initStock(Long showId, Long ticketTypeId, int stock) {
        String stockKey = getStockKey(showId, ticketTypeId);
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        log.info("库存初始化：showId={}, ticketTypeId={}, stock={}", showId, ticketTypeId, stock);
    }

    /**
     * DB 库存条件更新（乐观锁兜底）
     *
     * @param ticketTypeId 票档ID
     * @param quantity     扣减数量
     * @return 影响行数
     */
    public int updateDbStock(Long ticketTypeId, int quantity) {
        return ticketTypeManager.updateStock(ticketTypeId, quantity);
    }
}
