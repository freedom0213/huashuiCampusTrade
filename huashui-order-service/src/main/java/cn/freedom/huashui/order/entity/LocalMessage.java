package cn.freedom.huashui.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息表，对应 {@code t_local_message}。
 *
 * <p>承载本项目的核心可靠性设计：把「要发一条消息」这件事也当成一条<b>本地数据</b>，
 * 与业务变更放在同一个本地事务里落库。这样就把跨服务的分布式事务
 * 降级成了「本地事务 + 异步重试」——不需要引入 Seata 这类框架。
 *
 * <p>没有 {@code deleted} 字段：这些记录是可靠性追踪凭据，不允许删除。
 *
 * @author freedom0213
 */
@Data
@TableName("t_local_message")
public class LocalMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 消息唯一 id。既写进消息体（供消费端幂等），也是 Confirm 回调定位本记录的键 */
    private String msgId;

    private String exchange;

    private String routingKey;

    /** 业务类型，如 ORDER_CANCELED */
    private String bizType;

    /** 业务主键，如订单号。用于人工排查「是哪笔业务的消息」 */
    private String bizId;

    /** 消息体 JSON。保存原文而不是引用业务表，避免「业务数据被改后消息内容跟着变」 */
    private String payload;

    /** 状态：0 待发送，1 已发送，3 失败待人工处理（{@code LocalMessageStatus}） */
    private Integer status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次可重试时间，按退避递增 */
    private LocalDateTime nextRetryTime;

    /** 最后一次失败原因（截断到 500 字符，与表定义一致） */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
