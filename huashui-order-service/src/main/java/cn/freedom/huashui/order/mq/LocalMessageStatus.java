package cn.freedom.huashui.order.mq;

/**
 * 本地消息表的状态值。
 *
 * <p>用常量而不是枚举：这几个值只在「更新语句的条件与赋值」里出现，
 * 不需要按 code 反查描述，枚举的 {@code of()} 之类的配套反而多余。
 *
 * @author freedom0213
 */
public final class LocalMessageStatus {

    private LocalMessageStatus() {
    }

    /** 待发送：已落库但尚未确认投递成功。补发任务会扫描这个状态 */
    public static final int PENDING = 0;

    /** 已发送：Broker 已确认收到（Publisher Confirm 返回 ack） */
    public static final int SENT = 1;

    /**
     * 失败待人工处理：重试次数用尽。
     * <p><b>刻意不自动丢弃</b> —— 记录本身就是证据，留着才能排查；
     * 自动删除只会让「消息去哪了」变成无法回答的问题。
     */
    public static final int FAILED = 3;

    // 表定义里还有一个 2（已确认），表示「消费端已处理完成」。
    // 本阶段不使用它：那需要消费端反向回报一次，等于为一条消息再加一条消息，
    // 而它的价值（知道消息真的被处理了）在本项目的业务形态下并不成立 ——
    // 「商品是否已恢复在售」直接查商品状态就能知道，没必要让消息系统再回答一遍。
}
