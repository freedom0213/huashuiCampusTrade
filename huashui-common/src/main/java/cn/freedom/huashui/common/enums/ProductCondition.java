package cn.freedom.huashui.common.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 商品成色。
 *
 * <p><b>数值越小越新</b>，因此筛选「≥ 某档」要换算成 {@code condition_level <= code}：
 * 例如筛「九成新及以上」= {@code condition_level <= 1}。
 *
 * <p>刻意不加数据库索引：只有 4 个取值、选择性极低，单列索引的收益为零。
 * 成色是与校区、排序叠加使用的一个筛选项，由 MySQL 在命中其他索引后做残余过滤即可。
 *
 * @author freedom0213
 */
@Getter
public enum ProductCondition {

    BRAND_NEW(0, "全新"),
    NINETY_PERCENT(1, "九成新"),
    SEVENTY_PERCENT(2, "七成新"),
    FIFTY_PERCENT_AND_BELOW(3, "五成新及以下");

    private final Integer code;
    private final String desc;

    ProductCondition(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ProductCondition of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(condition -> condition.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public static boolean isValid(Integer code) {
        return of(code) != null;
    }

    /** 未经用户选择时的兜底档位：校园二手最常见的是九成新 */
    public static ProductCondition defaultCondition() {
        return NINETY_PERCENT;
    }
}
