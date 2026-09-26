package cn.freedom.huashui.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单，对应 {@code t_orders}。
 *
 * <p><b>本实体刻意没有 {@code deleted} 字段。</b>
 * 订单是交易凭证，属于业务上不允许删除的数据，因此表里就没有这一列，
 * 这里的实体也就不加 {@code @TableLogic}。
 * （application.yml 里配了全局的 {@code logic-delete-field: deleted}，
 * 但它只对实体中真实存在的同名字段生效，不会给本表凭空加条件。）
 *
 * <p><b>三个商品快照字段（title / cover / price）是刻意冗余的</b>：
 * 一是业务刚需——成交价必须快照，卖家事后改价不能影响历史订单；
 * 二是顺带解决跨库查询——order 库不必 JOIN product 库的表。
 * 规则：订单一旦创建，快照不再与商品同步。
 *
 * @author freedom0213
 */
@Data
@TableName("t_orders")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号：时间戳 + 随机数，不暴露真实单量 */
    private String orderNo;

    private Long buyerId;

    private Long sellerId;

    private Long productId;

    /** 商品标题快照 */
    private String productTitle;

    /** 商品封面快照 */
    private String productCover;

    /** 商品单价快照（成交价） */
    private BigDecimal productPrice;

    /**
     * 交易地点快照。面交场景买家据此赴约，必须快照——商品成交后被逻辑删除，
     * 靠 productId 反查会查不到。创建后不再与商品同步。
     */
    private String tradePlace;

    /** 订单总额。当前一单一商品，等于单价；保留该字段以便将来支持多商品 */
    private BigDecimal totalAmount;

    /** 状态：0 待支付，1 已付款，2 交易完成，3 已取消（{@code OrderStatus}） */
    private Integer status;

    /** 支付截止时间。超时取消任务据此字段扫描 */
    private LocalDateTime closeDeadline;

    private LocalDateTime payTime;

    private LocalDateTime finishTime;

    private LocalDateTime cancelTime;

    private String cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
