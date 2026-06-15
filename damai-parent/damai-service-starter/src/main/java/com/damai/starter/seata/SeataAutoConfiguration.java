package com.damai.starter.seata;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Seata AT 自动配置 —— 对应技术文档 §8.3。
 *
 * <p>职责：
 * <ul>
 *   <li>声明式条件装配，仅当引入 seata-spring-boot-starter 且配置 enable 时生效。</li>
 *   <li>实际 AT 模式数据源代理、全局事务扫描由 seata-spring-boot-starter 自动完成。</li>
 * </ul>
 *
 * <p>事务边界与长事务规避（技术文档 §8.3.2 —— 关键设计）：
 * <table>
 *   <tr><th>操作</th><th>纳入全局事务</th><th>原因</th></tr>
 *   <tr><td>order 写订单 (DB)</td><td>✅ 纳入</td><td>核心写</td></tr>
 *   <tr><td>program 扣 Redis 库存</td><td>❌ 不纳入</td><td>Redis 非 Seata RM</td></tr>
 *   <tr><td>program 扣 DB 库存</td><td>✅ 纳入</td><td>核心写</td></tr>
 *   <tr><td>发 Kafka (异步通知)</td><td>❌ 不纳入</td><td>IO 耗时，放事务外</td></tr>
 *   <tr><td>出票 / 通知</td><td>❌ 不纳入</td><td>耗时，异步</td></tr>
 * </table>
 *
 * <p>使用示例：
 * <pre>
 *   &#64;GlobalTransactional(name = "damai-create-order", rollbackFor = Exception.class)
 *   public void createOrderWithTransaction(...) {
 *       // 仅关键 DB 写操作
 *       orderManager.insertOrder(order);
 *       // Kafka 发送在事务外（事务提交后）
 *   }
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.seata.spring.annotation.GlobalTransactional")
@ConditionalOnProperty(prefix = "seata", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SeataAutoConfiguration {

    // Seata AT 的数据源代理、全局事务扫描器由 spring-cloud-starter-alibaba-seata 自动配置
    // 此处仅声明条件装配入口，各业务服务在需要分布式事务的方法上加 @GlobalTransactional 即可

}
