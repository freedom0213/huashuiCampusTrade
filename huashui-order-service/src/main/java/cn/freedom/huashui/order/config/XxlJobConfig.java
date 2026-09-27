package cn.freedom.huashui.order.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 执行器（订单服务）。
 *
 * <p><b>执行器是什么：</b>它不是一个独立的程序，而是<b>嵌在业务服务里的一段 SDK</b>——
 * 启动时在本地开一个 HTTP 端口（{@code xxl.job.executor.port}），
 * 向调度中心注册自己；调度中心按 cron 触发时，反向调用执行器的
 * {@code /run} 接口，执行器再回调本地 {@code @XxlJob} 方法。
 *
 * <p><b>为什么每个服务单独写一份配置类，而不是放进 common：</b>
 * 各服务的 {@code appname} 与端口不同（order 6005 / product 6006），
 * 且<b>不是所有服务都需要执行器</b>（网关不需要）。
 * 放进 common 会造成「引了 common 就被动获得一个执行器端口」，属于隐式副作用。
 *
 * <p><b>⚠️ 为什么显式配置 {@code xxl.job.executor.address}：</b>
 * 调度中心跑在 Docker 容器里、执行器跑在宿主机上。若不显式指定，
 * 执行器会自动探测本机 IP 注册（可能是容器网络到不了的地址），
 * 调度中心触发时就连不上，表现为任务日志里的「执行器地址不可用」。
 * 显式写成 {@code http://host.docker.internal:<port>} 后，
 * 容器内的调度中心可以用这个名字解析回宿主机（Docker Desktop 内置支持）。
 * <b>这是本项目 Docker 化调度中心唯一需要特殊处理的地方。</b>
 *
 * <p><b>调度中心没启动时会怎样：</b>执行器照常启动、注册失败只打日志，
 * 业务功能不受影响；只是「注册到调度中心」的任务不会被触发。
 * 开发时若不想起调度中心，把 {@code huashui.order.timeout-task-enabled} /
 * {@code local-message-task-enabled} 改回 {@code true} 即可让本地 {@code @Scheduled} 顶上。
 *
 * @author freedom0213
 */
@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses:}")
    private String adminAddresses;

    @Value("${xxl.job.access-token:}")
    private String accessToken;

    @Value("${xxl.job.executor.appname}")
    private String appName;

    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    @Value("${xxl.job.executor.port}")
    private int port;

    @Value("${xxl.job.executor.logpath}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appName);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);
        log.info("XXL-JOB 执行器已装配 | appname={} | address={} | admin={}",
                appName, address.isBlank() ? "自动探测" : address, adminAddresses);
        return executor;
    }
}
