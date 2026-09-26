package cn.freedom.huashui.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 订单状态机。
 *
 * <pre>
 *   待支付 ──买家确认已线下付款──→ 已付款 ──买家确认收货──→ 交易完成（终态）
 *     │
 *     └─ 买家主动取消 / XXL-JOB 扫描超时 ──→ 已取消（终态）
 *                                            └─ MQ 通知商品域恢复「在售」
 * </pre>
 *
 * @author freedom0213
 */
@Getter
public enum OrderStatus {

    /** 已创建订单、商品已锁定，等待买家付款 */
    WAITING_PAY(0, "待支付"),

    /** 买家确认已线下完成付款，商品置为已售出 */
    PAID(1, "已付款"),

    /** 交易完成，终态 */
    COMPLETED(2, "交易完成"),

    /** 已取消，终态；商品需恢复为在售 */
    CANCELLED(3, "已取消");

    private final Integer code;
    private final String desc;

    OrderStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 终态订单不允许再发生任何流转。
     */
    public boolean isFinalStatus() {
        return this == COMPLETED || this == CANCELLED;
    }
}
