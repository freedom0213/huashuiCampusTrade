package cn.freedom.huashui.product.controller;

import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.product.mq.MqDeadLetterService;
import cn.freedom.huashui.product.vo.DeadLetterVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MQ 运维接口：死信队列的查看与重放。
 *
 * <p>路径在 {@code /inner/**} 下，网关路由只覆盖 {@code /api/**}，
 * 所以外部<b>无法从网关访问</b>——这类接口能重放消息，绝不能对外暴露。
 * 将来若要在管理端提供入口，应当由管理端服务转调，而不是把它加到网关白名单。
 *
 * @author freedom0213
 */
@RestController
@RequestMapping("/inner/mq")
@RequiredArgsConstructor
@Tag(name = "内部接口", description = "MQ 死信查看与重放，仅供排查使用，不对网关暴露")
public class InnerMqController {

    private final MqDeadLetterService mqDeadLetterService;

    @GetMapping("/dead-letters")
    @Operation(summary = "查看死信队列",
            description = "需要传入 count（可选）。消息取出后会立刻放回，不产生消费。"
                    + "管理 API 不可用时仍返回积压条数")
    public Result<DeadLetterVO> deadLetters(
            @RequestParam(value = "count", required = false) Integer count) {
        return Result.success(mqDeadLetterService.peekDeadLetters(count == null ? 0 : count));
    }

    @PostMapping("/dead-letters/replay")
    @Operation(summary = "重放死信消息",
            description = "把死信队列里的消息重新投回业务交换机，让消费端再处理一次。"
                    + "limit 为本次最多重放条数（可选）")
    public Result<Integer> replay(
            @RequestParam(value = "limit", required = false) Integer limit) {
        int replayed = mqDeadLetterService.replayDeadLetters(limit == null ? 0 : limit);
        return Result.success("重放完成，共 " + replayed + " 条", replayed);
    }
}
