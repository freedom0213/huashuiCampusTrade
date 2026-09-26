package cn.freedom.huashui.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分类展示对象。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "商品分类")
public class CategoryVO {

    private Long id;

    private String name;

    private Integer sort;
}
