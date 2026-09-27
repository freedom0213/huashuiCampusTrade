package cn.freedom.huashui.product.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 执行器（商品服务）。
 *
 * <p>执行器是嵌在业务服务里的 SDK，启动后在本地开一个 HTTP 端口供调度中心回调，
 * 详见 order-service 的 {@code XxlJobConfig} 注释（那里解释了
 * 为什么每个服务各写一份、以及为什么必须显式配置 address）。
 *
 * <p>本服务用它跑「浏览量结算」：把 Redis 中累加的浏览量按差值回写到数据库。
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
