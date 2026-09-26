package cn.freedom.huashui.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 商品状态机。
 *
 * <pre>
 *   待审核 ──通过──→ 在售 ──被下单──→ 已锁定 ──买家付款──→ 已售出（终态）
 *     │              │                 │
 *     └─驳回→ 已驳回  └─卖家下架→ 已下架    └─订单取消/超时→ 回到在售
 * </pre>
 *
 * <p>状态流转必须由「条件更新」保证（{@code WHERE status = 前置状态}），
 * 靠受影响行数判断是否流转成功，不能用先查后改。
 *
 * @author freedom0213
 */
@Getter
public enum ProductStatus {

    /** 已提交，等待管理员审核 */
    PENDING_AUDIT(0, "待审核"),

    /** 在售，可被下单 */
    ON_SALE(1, "在售"),

    /** 已被下单锁定，等待买家付款；此状态下其他人不能下单 */
    LOCKED(2, "已锁定"),

    /** 交易完成，终态 */
    SOLD(3, "已售出"),

    /** 卖家主动下架（只影响新订单，已有订单继续有效） */
    OFF_SHELF(4, "已下架"),

    /** 审核不通过，卖家可修改后重新提交 */
    REJECTED(5, "已驳回");

    private final Integer code;
    private final String desc;

    ProductStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ProductStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public static boolean isOnSale(Integer code) {
        return ON_SALE.code.equals(code);
    }

    public boolean isFinalStatus() {
        return this == SOLD;
    }
}
