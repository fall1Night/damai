package com.damai.order.service;

import com.damai.common.api.PageResult;
import com.damai.order.entity.Order;
import com.damai.order.entity.OrderDetail;

import java.util.List;
import java.util.Map;

/**
 * 订单服务接口 —— 对应 PRD §8 抢票下单全链路。
 *
 * @author damai
 */
public interface OrderService {

    /**
     * 生成幂等令牌：UUID → Redis，TTL 10min。
     * <p>用户进入下单页时调用，提交下单时携带令牌。
     *
     * @param userId 用户ID
     * @return 幂等令牌（UUID）
     */
    String generateToken(Long userId);

    /**
     * 抢票下单核心链路 ⭐
     * <p>对应 PRD §8.1 全链路：
     * <ol>
     *   <li>幂等校验：Redis GETDEL 原子消费令牌</li>
     *   <li>防重锁：Redis 短窗口 5s 分布式锁</li>
     *   <li>库存预扣：Feign 调用 program 服务 Lua 预扣</li>
     *   <li>落订单：DB 写入待支付订单（Seata AT 包裹）</li>
     *   <li>本地消息表：与订单同一事务写入</li>
     *   <li>延迟消息：Kafka 投递超时取消消息（15min）</li>
     *   <li>异步通知：Kafka 投递"订单已创建"消息</li>
     *   <li>返回：订单号 + 待支付金额 + 支付截止时间</li>
     * </ol>
     *
     * @param userId     用户ID
     * @param showId     场次ID
     * @param ticketTypeId 票档ID
     * @param quantity   购买数量
     * @param viewerInfo 观演人信息快照（JSON）
     * @param token      幂等令牌
     * @return 订单创建结果（orderId, totalAmount, payDeadline）
     */
    Map<String, Object> createOrder(Long userId, Long showId, Long ticketTypeId,
                                     Integer quantity, String viewerInfo, String token);

    /**
     * 主动取消订单
     * <p>校验状态=待支付 → 归还 Redis 库存 → 更新订单状态 → 异步归还 DB 库存
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     */
    void cancelOrder(Long userId, Long orderId);

    /**
     * 按状态分页查询订单（命中分片，高效）
     *
     * @param userId   用户ID（分片键）
     * @param status   订单状态（可选）
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    PageResult<Order> queryOrders(Long userId, Integer status, int pageNum, int pageSize);

    /**
     * 订单详情（含明细）
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单详情（含明细列表）
     */
    Map<String, Object> queryOrderDetail(Long userId, Long orderId);
}
