package cn.freedom.huashui.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建订单参数。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "创建订单参数")
public class CreateOrderDTO {

    @NotNull(message = "商品 id 不能为空")
    @Schema(description = "要购买的商品 id")
    private Long productId;
}
