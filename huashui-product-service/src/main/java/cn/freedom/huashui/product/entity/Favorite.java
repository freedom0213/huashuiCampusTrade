package cn.freedom.huashui.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收藏，对应 {@code t_favorite}。
 *
 * <p>本实体没有 {@code deleted} 字段：收藏是「有 / 没有」的二元关系，
 * 取消收藏时直接物理删除即可，不需要留痕，也就不需要逻辑删除。
 * 这与订单不同（订单是交易凭证，才必须保留）。
 *
 * @author freedom0213
 */
@Data
@TableName("t_favorite")
public class Favorite implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long productId;

    /** 收藏时间。注意表里没有 update_time —— 收藏不会被修改，只有创建与删除 */
    private LocalDateTime createTime;
}
