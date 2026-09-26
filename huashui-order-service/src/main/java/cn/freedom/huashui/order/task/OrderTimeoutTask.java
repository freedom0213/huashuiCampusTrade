package cn.freedom.huashui.order.task;

import cn.freedom.huashui.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未付款订单的兜底清理任务。
 *
 * <p><b>它解决什么问题：</b>
 * 下单会把商品锁定。如果买家下单后既不付款也不取消，商品就永远停在「已锁定」——
 * 没有任何其它机制会去解锁它。这个任务就是那个机制。
 *
 * <p><b>为什么是本地 {@code @Scheduled} 而不是 XXL-JOB：</b>
 * 分布式任务调度是本项目阶段 10 的内容。但「商品被永久锁死」是个功能性缺陷，
 * 不能等到阶段 10 才解决，所以先用本地定时任务顶上。
 * 阶段 10 接入 XXL-JOB 后，把 {@code huashui.order.timeout-task-enabled} 改成
 * {@code false} 即可停用它，不需要改代码。
 *
 * <p><b>多实例下的重复执行不是问题</b>：任务内部走的是条件更新
 * （{@code WHERE status = 待支付}），两个实例同时扫到同一笔订单时只有一方能改成功。
 * 这也是为什么本项目可以先用本地定时任务——不引入分布式锁也不会出错。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "huashui.order",
        name = "timeout-task-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderTimeoutTask {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${huashui.order.timeout-task-interval-ms:60000}")
    public void closeExpiredOrders() {
        try {
            orderService.closeExpiredOrders();
        } catch (Exception e) {
            // 定时任务里抛出的异常会终止本次调度，日志里只有一行堆栈，
            // 很容易被忽略；而它一旦静默停摆，「商品锁死」就会变成没人知道的事。
            // 所以这里必须自己兜住异常，保证任务一直活着
            log.error("超时取消订单任务执行异常", e);
        }
    }
}
