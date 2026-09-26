package cn.freedom.huashui.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图对象。列表与详情共用（详情无非是多几个字段，为一个字段再定义一个 VO 不值得）。
 *
 * <p>只暴露订单自己的字段，<b>不做跨服务聚合</b>：对端用户的昵称/头像由前端并行调用
 * {@code /api/user/detail/{id}} 获取。这样做详情页首屏更快（卖家信息可以先出骨架），
 * 也避免订单服务为了补一个昵称而依赖用户服务的可用性。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "订单信息")
public class OrderVO {

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "买家 id")
    private Long buyerId;

    @Schema(description = "卖家 id")
    private Long sellerId;

    @Schema(description = "当前登录用户在这笔订单里的角色：BUYER / SELLER")
    private String role;

    @Schema(description = "商品 id")
    private Long productId;

    @Schema(description = "商品标题（下单时的快照）")
    private String productTitle;

    @Schema(description = "商品封面（下单时的快照）")
    private String productCover;

    @Schema(description = "成交单价")
    private BigDecimal productPrice;

    @Schema(description = "交易地点（下单时的快照）。面交场景买家据此赴约")
    private String tradePlace;

    @Schema(description = "订单总额")
    private BigDecimal totalAmount;

    @Schema(description = "状态：0 待支付，1 已付款，2 交易完成，3 已取消")
    private Integer status;

    @Schema(description = "状态中文描述，前端不必自己维护映射表")
    private String statusDesc;

    @Schema(description = "支付截止时间")
    private LocalDateTime closeDeadline;

    /**
     * 距支付截止还剩多少秒。仅「待支付」有值，其余为 0，前端据此渲染倒计时。
     *
     * <p><b>刻意用 {@code Integer} 而不是 {@code Long}：</b>
     * common 的全局序列化规则把 Long 一律转成字符串（为了让雪花 ID 在前端不丢精度）。
     * 倒计时属于「数量」而不是「ID」，必须是数字——若返回字符串，
     * 前端写 {@code remainSeconds + 1} 会得到 "17991"（JS 的 + 会做字符串拼接），
     * 而不是 1800。这类错误不报错、只算错。
     * <p>约定：<b>Long 只用于 ID，数量类字段一律用 int / Integer。</b>
     */
    @Schema(description = "距支付截止还剩多少秒（数字）。仅「待支付」有值，其余为 0")
    private Integer remainSeconds;

    @Schema(description = "付款时间")
    private LocalDateTime payTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "取消时间")
    private LocalDateTime cancelTime;

    @Schema(description = "取消原因")
    private String cancelReason;

    @Schema(description = "下单时间")
    private LocalDateTime createTime;
}
