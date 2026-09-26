package cn.freedom.huashui.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 校区。
 *
 * <p>华北水利水电大学的三个校区（已核实官网地址）：
 * <ul>
 *   <li>龙子湖 —— 河南省郑州市金水东路 136 号</li>
 *   <li>花园 —— 河南省郑州市北环路 36 号</li>
 *   <li>江淮 —— 河南省信阳市罗山县龙池大道 236 号</li>
 * </ul>
 *
 * <p><b>为什么校区要单独成列，而不是从 {@code trade_place} 文本里解析：</b>
 * {@code trade_place} 是「给人看的展示文本」（如 {@code 龙子湖 · 第二食堂}），
 * 校区是「给机器筛的维度」。若靠 {@code LIKE '龙子湖%'} 去解析展示文本，
 * 一旦文案调整（改成 {@code 龙子湖校区 · 第二食堂}）筛选就会<b>静默失效</b>——
 * 不报错，只是查出来的结果不对，这类问题最难排查。
 *
 * <p>江淮校区在信阳，与郑州两个校区不通勤，因此校区筛选有真实业务意义：
 * 直接过滤掉自己到不了的商品。
 *
 * @author freedom0213
 */
@Getter
public enum Campus {

    LONGZIHU("龙子湖"),
    HUAYUAN("花园"),
    JIANGHUAI("江淮");

    /** 存库与接口传参统一用这个名称 */
    private final String name;

    Campus(String name) {
        this.name = name;
    }

    /**
     * 按名称查找，找不到返回 null。
     * 用于校验前端传入的校区参数，避免写错一个字就得到空列表。
     */
    public static Campus of(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        return Arrays.stream(values())
                .filter(campus -> campus.name.equals(trimmed))
                .findFirst()
                .orElse(null);
    }

    public static boolean isValid(String name) {
        return of(name) != null;
    }
}
