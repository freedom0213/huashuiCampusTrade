package cn.freedom.huashui.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品，对应 {@code t_product}。
 *
 * @author freedom0213
 */
@Data
@TableName("t_product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sellerId;

    private Long categoryId;

    /**
     * 校区（存短名：龙子湖 / 花园 / 江淮）。
     * <p>与 {@code tradePlace} 的分工：本字段<b>用于筛选</b>（有索引），
     * {@code tradePlace} 是<b>展示文本</b>（如「龙子湖 · 第二食堂」）。
     */
    private String campus;

    private String title;

    private String description;

    /** 成色：0 全新，1 九成新，2 七成新，3 五成新及以下（{@code ProductCondition}，数值越小越新） */
    private Integer conditionLevel;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String coverUrl;

    /** 交易地点完整展示文本，如「龙子湖 · 第二食堂」 */
    private String tradePlace;

    private Integer viewCount;

    private Integer favoriteCount;

    /** 状态：0 待审核，1 在售，2 已锁定，3 已售出，4 已下架，5 已驳回（{@code ProductStatus}） */
    private Integer status;

    private String rejectReason;

    private LocalDateTime publishTime;

    private LocalDateTime soldTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
