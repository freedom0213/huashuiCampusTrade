package cn.freedom.huashui.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 死信队列的查看结果。
 *
 * @author freedom0213
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "死信队列查看结果")
public class DeadLetterVO {

    @Schema(description = "队列名")
    private String queue;

    /**
     * 队列当前积压条数。
     * <p>它通过 AMQP 协议获取，<b>即使管理 API 不可用也能返回</b> ——
     * 而「有没有积压」往往是排查时第一个要看的信息。
     */
    @Schema(description = "队列积压条数（即便管理 API 不可用也能拿到）")
    private Integer queueDepth;

    @Schema(description = "消息摘要列表")
    private List<DeadLetterItemVO> items;

    @Schema(description = "管理 API 是否可用。false 时只返回积压条数，消息内容需去管理台看")
    private Boolean managementAvailable;

    @Schema(description = "不可用时的说明与建议")
    private String hint;
}
