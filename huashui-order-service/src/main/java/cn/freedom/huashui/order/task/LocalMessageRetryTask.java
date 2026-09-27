package cn.freedom.huashui.order.task;

import cn.freedom.huashui.order.service.LocalMessageRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 本地消息补发任务（本地 {@code @Scheduled} 版本）。
 *
 * <p><b>阶段 10 起默认关闭</b>（{@code huashui.order.local-message-task-enabled: false}），
 * 由 XXL-JOB 的 {@code localMessageRetryJob} 接管。保留它作为降级开关：
 * 不想启动调度中心时，把这个开关和 {@code timeout-task-enabled} 一起改回 true 即可照常运行。
 *
 * <p>具体的补发逻辑在 {@link LocalMessageRetryService}，本类只负责「什么时候触发」。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "huashui.order",
        name = "local-message-task-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LocalMessageRetryTask {

    private final LocalMessageRetryService localMessageRetryService;

    @Scheduled(fixedDelayString = "${huashui.order.local-message-task-interval-ms:30000}")
    public void retryPendingMessages() {
        try {
            localMessageRetryService.retryPendingMessages();
        } catch (Exception e) {
            // 定时任务里抛出的异常会终止本次调度，而它一旦静默停摆，
            // 「消息永远发不出去」就会变成没人知道的事，所以必须自己兜住
            log.error("本地消息补发任务执行异常", e);
        }
    }
}
