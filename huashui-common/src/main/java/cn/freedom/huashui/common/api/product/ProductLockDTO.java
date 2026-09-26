package cn.freedom.huashui.common.api.product;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品锁定结果，同时也是下单所需的商品快照。
 *
 * <p><b>为什么把「锁定商品」和「取商品快照」合成一次远程调用：</b>
 * 下单必须先锁定商品，锁定成功后立刻需要标题 / 封面 / 价格写入订单。
 * 如果拆成「先查快照、再锁定」两个接口：
 * <ol>
 *   <li>多一次远程往返；</li>
 *   <li>两次调用之间商品可能已被别人买走，拿到的是过期快照。</li>
 * </ol>
 * 合成一次调用，快照与锁定结果天然一致。
 *
 * @author freedom0213
 */
@Data
public class ProductLockDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品 id */
    private Long productId;

    /** 卖家 id。订单需要它来区分「我买到的」与「我卖出的」 */
    private Long sellerId;

    /** 商品标题，写入订单快照 */
    private String title;

    /** 商品封面，写入订单快照 */
    private String coverUrl;

    /** 成交单价，写入订单快照 */
    private BigDecimal price;

    /**
     * 交易地点，写入订单快照。
     *
     * <p>面交场景买家据此赴约。必须快照而不能靠 productId 反查商品：
     * 商品成交后被逻辑删除，订单详情页就会查不到地点。
     */
    private String tradePlace;
}
