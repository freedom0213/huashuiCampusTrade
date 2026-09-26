package cn.freedom.huashui.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 订单列表查询参数。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "订单列表查询参数")
public class OrderQueryDTO {

    @Schema(description = "视角：buyer 我买到的 / seller 我卖出的 / all 全部（默认）")
    private String role = "all";

    @Schema(description = "按状态筛选：0 待支付，1 已付款，2 交易完成，3 已取消")
    private Integer status;

    @Min(value = 1, message = "页码最小为 1")
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    @Schema(description = "每页条数", defaultValue = "10")
    private Integer size = 10;
}
