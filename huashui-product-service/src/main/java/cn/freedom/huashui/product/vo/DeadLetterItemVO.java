package cn.freedom.huashui.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条死信消息的摘要。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "死信消息摘要")
public class DeadLetterItemVO {

    @Schema(description = "消息 id，与业务侧的本地消息表记录对应")
    private String msgId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "商品 id")
    private Long productId;

    @Schema(description = "消息里的取消时间")
    private LocalDateTime cancelTime;

    @Schema(description = "进入死信的原因，如 rejected / expired / maxlen")
    private String deathReason;

    @Schema(description = "该消息被死信投递的累计次数")
    private Integer deathCount;

    @Schema(description = "消息体原文，便于人工核对")
    private String payload;
}
