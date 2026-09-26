package cn.freedom.huashui.order.service.impl;

import cn.freedom.huashui.common.api.product.ProductClient;
import cn.freedom.huashui.common.api.product.ProductLockDTO;
import cn.freedom.huashui.common.context.UserContext;
import cn.freedom.huashui.common.enums.OrderStatus;
import cn.freedom.huashui.common.exception.BizException;
import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.common.result.ResultCode;
import cn.freedom.huashui.order.config.OrderProperties;
import cn.freedom.huashui.order.dto.OrderQueryDTO;
import cn.freedom.huashui.order.entity.Order;
import cn.freedom.huashui.order.mapper.OrderMapper;
import cn.freedom.huashui.order.mq.OrderMessageSender;
import cn.freedom.huashui.order.service.OrderService;
import cn.freedom.huashui.order.service.PayService;
import cn.freedom.huashui.order.vo.OrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 交易服务实现。
 *
 * <p>几个贯穿全类的原则：
 * <ol>
 *   <li><b>状态流转一律用条件更新</b>（{@code WHERE status = 前置状态}）+ 判断受影响行数，
 *       不先查后改。并发下「先查后改」会让两个请求同时通过判断、同时执行变更。</li>
 *   <li><b>写操作只有一条 SQL 时不套 @Transactional</b>：InnoDB 单语句本身原子，
 *       套事务不会多任何保证，反而会给后人一个「事务边界」的错觉。</li>
 *   <li><b>消息在状态变更成功之后才发</b>，且发送失败不抛异常（理由见各方法注释）。</li>
 * </ol>
 *
 * @author freedom0213
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String CANCEL_REASON_TIMEOUT = "超时未付款，系统自动取消";
    private static final String CANCEL_REASON_BUYER = "买家主动取消";
    private static final String CANCEL_REASON_SELLER = "卖家主动取消";

    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final PayService payService;
    private final OrderMessageSender messageSender;
    private final OrderProperties orderProperties;

    /**
     * 编程式事务。为什么用它而不是 {@code @Transactional} 注解，
     * 见 {@link #transitToCancelled} 的注释 —— 那里有个很容易埋雷的坑。
     */
    private final TransactionTemplate transactionTemplate;

    // ==================== 下单 ====================

    @Override
    public OrderVO create(Long productId) {
        if (productId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "商品 id 不能为空");
        }
        Long buyerId = UserContext.requireUserId();

        // ① 锁定商品，同时拿到快照（一次远程调用完成两件事）。
        //    「商品不存在」「不能买自己发布的商品」「已被别人买走」都在这一步被拦下，
        //    此时还没有写任何数据，失败最干净
        ProductLockDTO snapshot = lockProduct(productId, buyerId);

        // ② 建单
        Order order = buildOrder(snapshot, buyerId);
        try {
            orderMapper.insert(order);
        } catch (Exception e) {
            // 跨服务没有事务：商品已锁定，但订单没建成。
            // 必须把商品放回去——否则它会永远停在「已锁定」，而且超时取消任务
            // 扫的是订单表，找不到指向它的订单，没有任何机制会去解锁它
            compensateUnlock(productId, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "下单失败，请稍后重试");
        }

        log.info("下单成功 | orderNo={} | productId={} | buyerId={} | sellerId={} | closeDeadline={}",
                order.getOrderNo(), productId, buyerId, snapshot.getSellerId(), order.getCloseDeadline());
        return toVO(order, buyerId);
    }

    // ==================== 读操作 ====================

    @Override
    public OrderVO detail(String orderNo) {
        Long userId = UserContext.requireUserId();
        Order order = requireOrder(orderNo);
        requireParticipant(order, userId);
        return toVO(order, userId);
    }

    @Override
    public PageResult<OrderVO> listMine(OrderQueryDTO query) {
        Long userId = UserContext.requireUserId();

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        String role = query.getRole() == null ? "" : query.getRole().trim().toLowerCase();
        switch (role) {
            case "buyer" -> wrapper.eq(Order::getBuyerId, userId);
            case "seller" -> wrapper.eq(Order::getSellerId, userId);
            // 默认 all：我参与的全部订单（买到的 + 卖出的）。
            // 必须用 and(...) 包一层，否则 or 会把外层其它条件一起放走
            default -> wrapper.and(w -> w.eq(Order::getBuyerId, userId)
                    .or().eq(Order::getSellerId, userId));
        }
        if (query.getStatus() != null) {
            wrapper.eq(Order::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Order::getCreateTime);
        // 兜底排序键：同一秒内下的两单若只按 create_time 排，
        // 翻页时顺序不稳定，会出现同一条重复出现、另一条翻不到
        wrapper.orderByDesc(Order::getId);

        Page<Order> page = new Page<>(query.getPage(), query.getSize());
        IPage<Order> result = orderMapper.selectPage(page, wrapper);
        List<OrderVO> records = result.getRecords().stream()
                .map(order -> toVO(order, userId))
                .toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public int soldCount(Long sellerId) {
        if (sellerId == null) {
            return 0;
        }
        // 「成交」= 已付款 + 交易完成。已取消不算成交
        long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getSellerId, sellerId)
                .in(Order::getStatus,
                        List.of(OrderStatus.PAID.getCode(), OrderStatus.COMPLETED.getCode())));
        return Math.toIntExact(count);
    }

    // ==================== 状态流转 ====================

    @Override
    public void pay(String orderNo) {
        Long userId = UserContext.requireUserId();
        Order order = requireOrder(orderNo);
        if (!Objects.equals(order.getBuyerId(), userId)) {
            throw new BizException(ResultCode.ORDER_NOT_OWNED, "只有买家可以确认付款");
        }

        // 先收款、再流转状态。接入真实支付后，这里就是「校验支付结果」的位置
        payService.confirmPayment(order.getOrderNo(), order.getTotalAmount());

        LocalDateTime now = LocalDateTime.now();
        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.WAITING_PAY.getCode())
                .set(Order::getStatus, OrderStatus.PAID.getCode())
                .set(Order::getPayTime, now)
                .set(Order::getUpdateTime, now));
        if (affected == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "只有「待支付」的订单可以确认付款");
        }

        // 商品置为已售出。注意这一步是「连带效果」，不是付款的前提：
        // 付款的既成事实是订单变为「已付款」，商品状态只是随之同步。
        // 因此这里失败不抛异常——抛了用户会看到「付款失败」，而订单其实已经付款了，
        // 重试又会被「只有待支付可确认付款」挡回去，彻底卡死。
        // 失败只记 ERROR；阶段 10 会补一个「订单已付款但商品仍锁定」的扫描任务兜底
        markProductSold(order);
        log.info("订单已付款 | orderNo={} | buyerId={}", orderNo, userId);
    }

    @Override
    public void complete(String orderNo) {
        Long userId = UserContext.requireUserId();
        Order order = requireOrder(orderNo);
        // 买卖双方都可以确认完成：本项目是校内当面自提，交割时双方都在场，
        // 限定成只有买家能操作，反而会出现「买家已走、卖家无法收尾」的情况
        requireParticipant(order, userId);

        LocalDateTime now = LocalDateTime.now();
        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PAID.getCode())
                .set(Order::getStatus, OrderStatus.COMPLETED.getCode())
                .set(Order::getFinishTime, now)
                .set(Order::getUpdateTime, now));
        if (affected == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "只有「已付款」的订单可以确认完成");
        }
        log.info("订单已完成 | orderNo={} | operator={}", orderNo, userId);
    }

    @Override
    public void cancel(String orderNo, String reason) {
        Long userId = UserContext.requireUserId();
        Order order = requireOrder(orderNo);
        requireParticipant(order, userId);

        String finalReason = StringUtils.hasText(reason)
                ? reason.trim()
                : defaultCancelReason(order, userId);

        // 这里刻意不加 @Transactional：本方法内只有 transitToCancelled 这一处写操作，
        // 而它内部用 TransactionTemplate 自己开了事务（订单流转 + 消息落库必须原子）。
        // 在外面再套一层只会让事务边界变模糊，不会多任何保证。
        //
        // 不能取消的状态：已付款之后。资金已线下交割、商品已是「已售出」终态，
        // 本项目不做退款与退货（总设已冻结），纠纷由双方线下解决。
        if (!transitToCancelled(order, finalReason)) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "只有「待支付」的订单可以取消");
        }
        log.info("订单已取消 | orderNo={} | operator={} | reason={}", orderNo, userId, finalReason);
    }

    @Override
    public int closeExpiredOrders() {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.WAITING_PAY.getCode())
                .lt(Order::getCloseDeadline, LocalDateTime.now())
                .orderByAsc(Order::getCloseDeadline);

        // 用分页插件限制条数，而不是 last("LIMIT n") —— 后者是字符串拼接，
        // 今天拼的是 int 很安全，但会立一个坏先例
        Page<Order> page = new Page<>(1, orderProperties.getTimeoutBatchSize());
        page.setSearchCount(false);   // 不需要总数，省一次 count 查询
        List<Order> expired = orderMapper.selectPage(page, wrapper).getRecords();

        int closed = 0;
        for (Order order : expired) {
            try {
                // 并发安全：多个实例同时扫到同一笔订单时，
                // 条件更新（status = 待支付）会让只有一方成功
                if (transitToCancelled(order, CANCEL_REASON_TIMEOUT)) {
                    closed++;
                }
            } catch (Exception e) {
                // 单笔失败不能中断整批
                log.error("超时取消订单失败 | orderNo={}", order.getOrderNo(), e);
            }
        }
        if (closed > 0) {
            log.info("超时取消任务：本轮取消 {} 笔（共扫描到 {} 笔）", closed, expired.size());
        }
        return closed;
    }

    // ==================== 私有方法 ====================

    /**
     * 把订单流转为「已取消」，返回是否真的发生了流转。
     *
     * <p>买家主动取消、卖家主动取消、超时自动取消三条路径共用，
     * 保证「状态变更 + 记录要发的消息」这一对动作只写一遍。
     *
     * <p><b>为什么这两件事必须在一个本地事务里：</b>
     * <ul>
     *   <li>只成功前一件 → 订单取消了，但没有任何机制会去解锁商品，<b>商品永久锁死</b>；</li>
     *   <li>只成功后一件 → 发出一个「取消了一笔其实没被取消的订单」的消息，
     *       商品被错误解锁，可能被第二个人买走，形成<b>超卖</b>。</li>
     * </ul>
     *
     * <p><b>为什么用 {@link TransactionTemplate} 而不是 {@code @Transactional} 注解：</b>
     * <ol>
     *   <li>超时取消任务是在循环里逐单处理的，把注解加在任务方法上会误伤成
     *       「一个长事务包住整批 200 单」，锁住大量行；</li>
     *   <li>本方法是<b>私有方法</b>，而 {@code @Transactional} 靠 Spring 代理生效，
     *       写在私有方法上<b>不会报错、也不会生效</b> —— 代码看起来有事务，实际在裸奔。
     *       这是本项目里最容易埋雷的一处，所以干脆用编程式事务，让事务边界在代码里看得见。</li>
     * </ol>
     */
    private boolean transitToCancelled(Order order, String reason) {
        Boolean changed = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderNo, order.getOrderNo())
                    .eq(Order::getStatus, OrderStatus.WAITING_PAY.getCode())
                    .set(Order::getStatus, OrderStatus.CANCELLED.getCode())
                    .set(Order::getCancelTime, now)
                    .set(Order::getCancelReason, reason)
                    .set(Order::getUpdateTime, now));
            if (affected == 0) {
                // 前置状态已不匹配（这段时间里被付款了、或已被别人取消）→ 直接结束
                return false;
            }
            // 与上面那条 UPDATE 同事务落库。
            // 真正的投递由 OrderMessageSender 注册的「提交后回调」触发，不在这里发
            messageSender.recordOrderCancelled(order, reason);
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    private Order buildOrder(ProductLockDTO snapshot, Long buyerId) {
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setBuyerId(buyerId);
        order.setSellerId(snapshot.getSellerId());
        order.setProductId(snapshot.getProductId());
        order.setProductTitle(snapshot.getTitle());
        order.setProductCover(snapshot.getCoverUrl());
        order.setProductPrice(snapshot.getPrice());
        // 面交地点快照：商品售出后可能被逻辑删除，订单页不能依赖反查商品
        order.setTradePlace(snapshot.getTradePlace());
        // 当前一单一商品，总额 = 单价。保留该字段是为了将来支持多商品时不改表
        order.setTotalAmount(snapshot.getPrice());
        order.setStatus(OrderStatus.WAITING_PAY.getCode());
        order.setCancelReason("");
        order.setCloseDeadline(LocalDateTime.now().plusMinutes(orderProperties.getPayTimeoutMinutes()));
        return order;
    }

    /**
     * 生成订单号：14 位时间 + 6 位随机。
     *
     * <p>前 14 位可读（出问题时一眼看出下单时间），后 6 位打散以避免同一秒内并发下单撞号。
     * 万一还是撞了，{@code uk_order_no} 唯一索引会拦住，不会产生重复订单。
     */
    private String generateOrderNo() {
        return ORDER_NO_TIME.format(LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private ProductLockDTO lockProduct(Long productId, Long buyerId) {
        Result<ProductLockDTO> result;
        try {
            result = productClient.lock(productId, buyerId);
        } catch (Exception e) {
            // Feign 会抛异常只有两种情况：连不上商品服务，或对方返回了非 2xx。
            // 「商品已被别人买走」这类业务失败走的是 HTTP 200 + 业务码，不会落到这里
            log.error("调用商品服务锁定商品失败 | productId={} | buyerId={}", productId, buyerId, e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "商品服务暂时不可用，请稍后重试");
        }
        return unwrap(result, "锁定商品失败，请稍后重试");
    }

    private void markProductSold(Order order) {
        try {
            Result<Void> result = productClient.markSold(order.getProductId());
            if (result == null || !result.isSuccess()) {
                log.error("订单已付款，但商品标记已售出失败 | orderNo={} | productId={} | 响应={}",
                        order.getOrderNo(), order.getProductId(), result);
            }
        } catch (Exception e) {
            log.error("订单已付款，但调用商品服务标记已售出失败 | orderNo={} | productId={}",
                    order.getOrderNo(), order.getProductId(), e);
        }
    }

    private void compensateUnlock(Long productId, Exception cause) {
        log.error("建单失败，开始补偿解锁商品 | productId={}", productId, cause);
        try {
            productClient.unlock(productId);
            log.info("补偿解锁成功 | productId={}", productId);
        } catch (Exception e) {
            // 补偿也失败 → 商品会一直停在「已锁定」，且没有订单指向它，
            // 超时取消任务（扫订单表）永远扫不到。这是本方案唯一的脏状态，
            // 阶段 10 会用「已锁定超过 N 分钟且无有效订单」的扫描任务兜底；
            // 在此之前，这条日志是唯一的发现途径
            log.error("补偿解锁商品失败，商品将保持锁定，需人工介入 | productId={}", productId, e);
        }
    }

    /**
     * 拆开下游返回的统一响应体：成功取数据，失败把业务码原样抬上来。
     *
     * <p>为什么原样透出而不是统一成「操作失败」：前端要据此区分
     * 「手慢了，商品已被别人买走」（提示刷新列表）与「系统繁忙」（提示重试）。
     * 抹平错误码等于把可用信息丢掉。
     */
    private <T> T unwrap(Result<T> result, String fallbackMessage) {
        if (result == null) {
            throw new BizException(ResultCode.SYSTEM_ERROR, fallbackMessage);
        }
        if (result.isSuccess()) {
            return result.getData();
        }
        ResultCode code = ResultCode.of(result.getCode());
        if (code == null) {
            throw new BizException(result.getCode(), result.getMessage());
        }
        throw new BizException(code, result.getMessage());
    }

    private Order requireOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo.trim()));
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void requireParticipant(Order order, Long userId) {
        if (!Objects.equals(order.getBuyerId(), userId)
                && !Objects.equals(order.getSellerId(), userId)) {
            throw new BizException(ResultCode.ORDER_NOT_OWNED, "只能操作与自己相关的订单");
        }
    }

    private String defaultCancelReason(Order order, Long operatorId) {
        return Objects.equals(order.getBuyerId(), operatorId) ? CANCEL_REASON_BUYER : CANCEL_REASON_SELLER;
    }

    private OrderVO toVO(Order order, Long currentUserId) {
        OrderVO vo = new OrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setRole(Objects.equals(order.getBuyerId(), currentUserId) ? "BUYER" : "SELLER");
        vo.setProductId(order.getProductId());
        vo.setProductTitle(order.getProductTitle());
        vo.setProductCover(order.getProductCover());
        vo.setProductPrice(order.getProductPrice());
        vo.setTradePlace(order.getTradePlace());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        OrderStatus status = OrderStatus.of(order.getStatus());
        vo.setStatusDesc(status == null ? "" : status.getDesc());
        vo.setCloseDeadline(order.getCloseDeadline());
        vo.setRemainSeconds(remainSeconds(order));
        vo.setPayTime(order.getPayTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setCancelReason(order.getCancelReason());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    /**
     * 剩余支付秒数。只有「待支付」才有意义——已付款/已取消的订单再给倒计时，
     * 前端还得自己判断该不该显示，不如服务端直接给 0。负数一律归零，
     * 否则前端倒计时会出现「-3 秒」这种显示。
     *
     * <p><b>返回 Integer 而不是 Long</b>：common 把 Long 全局序列化成了字符串
     * （为了雪花 ID 在前端不丢精度），而倒计时是数量、必须保持数字，
     * 否则前端 {@code remainSeconds + 1} 会变成字符串拼接。详见 {@code OrderVO}。
     */
    private Integer remainSeconds(Order order) {
        if (!Objects.equals(OrderStatus.WAITING_PAY.getCode(), order.getStatus())
                || order.getCloseDeadline() == null) {
            return 0;
        }
        long seconds = Duration.between(LocalDateTime.now(), order.getCloseDeadline()).getSeconds();
        if (seconds <= 0L) {
            return 0;
        }
        // 支付超时以分钟计，实际不可能接近 int 上限；这里显式收敛，
        // 免得读者还要自己去推「会不会溢出」
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }
}
