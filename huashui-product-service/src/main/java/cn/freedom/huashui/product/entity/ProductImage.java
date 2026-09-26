package cn.freedom.huashui.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品图片，对应 {@code t_product_image}。
 *
 * <p>与商品表一对多拆开，避免把多张图片塞进商品行（那样既违反第一范式，
 * 也没法给图片单独排序）。
 *
 * @author freedom0213
 */
@Data
@TableName("t_product_image")
public class ProductImage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long productId;

    private String url;

    /** 展示顺序，越小越靠前；第一条即封面 */
    private Integer sort;

    private LocalDateTime createTime;
}
