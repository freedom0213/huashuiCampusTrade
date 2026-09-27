package cn.freedom.huashui.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 交易域可调参数。
 *
 * @author freedom0213
 */
@Data
@ConfigurationProperties(prefix = "huashui.order")
public class OrderProperties {

    /**
     * 待支付订单的超时分钟数。
     * 下单时据此算出 {@code close_deadline}。演示时可以调成 1 分钟。
     */
    private int payTimeoutMinutes = 30;

    /**
     * 是否启用「超时订单自动取消」的兜底定时任务。
     *
     * <p>正式方案是 XXL-JOB（阶段 10）。在那之前先用一个 {@code @Scheduled} 顶上：
     * 没有它的话，下单后不付款会把商品<b>永久锁死</b>——因为没有别的机制会去解锁。
     * 阶段 10 接入 XXL-JOB 后把本开关关掉即可，不需要改代码。
     */
    private boolean timeoutTaskEnabled = true;

    /** 兜底任务每轮最多处理多少笔超时订单。分批处理，避免一次锁住过多行 */
    private int timeoutBatchSize = 200;

    /** 兜底任务的执行间隔（毫秒） */
    private long timeoutTaskIntervalMs = 60000L;

    // ==================== 本地消息表（消息可靠性） ====================

    /** 最大补发次数。超过则转为「失败待人工处理」，不再自动重试 */
    private int localMessageMaxRetry = 5;

    /** 补发任务是否启用（正式方案是阶段 10 的 XXL-JOB，届时关掉本开关即可） */
    private boolean localMessageTaskEnabled = true;

    /** 补发任务每轮最多处理多少条 */
    private int localMessageBatchSize = 100;

    /** 补发任务的执行间隔（毫秒） */
    private long localMessageTaskIntervalMs = 30000L;

    /**
     * 「锁定超时」判定在支付超时之上额外加的缓冲分钟数。
     *
     * <p>为什么要有缓冲：正常的下单到付款之间，商品本来就处于「已锁定」，那是「进行中」
     * 而不是「异常」。若把阈值卡在支付超时的同一时刻，正在交易的商品会被误判成异常并强行解锁，
     * 出现「买家还在付款，商品已被放回在售、被别人买走」。
     */
    private int staleLockBufferMinutes = 5;

    /** 单轮扫描最多处理多少个候选商品，避免一次拉太多把商品服务拖慢 */
    private int staleLockBatchSize = 100;
}
