package cn.freedom.huashui.common.config;

import cn.freedom.huashui.common.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证统一 JSON 序列化规则。
 *
 * <p>这里的每一条都对应一个「不报错但结果错」的真实风险，
 * 因此必须用测试锁住，而不是靠肉眼看接口返回。
 *
 * @author freedom0213
 */
class JacksonAutoConfigurationTest {

    /** 与 Spring Boot 加载本配置时的方式保持一致 */
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonAutoConfiguration().huashuiJacksonCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("Long 序列化为字符串 —— 雪花 ID 超出 JS 安全整数范围会丢精度")
    void longShouldBeSerializedAsString() throws Exception {
        // 19 位雪花 ID；若按数字返回，JS 会把它变成 ...9185900
        String json = OBJECT_MAPPER.writeValueAsString(Map.of("id", 2103749049839185921L));

        assertThat(json).isEqualTo("{\"id\":\"2103749049839185921\"}");
    }

    @Test
    @DisplayName("分页的计数字段保持数字类型 —— 前端需要做数值比较")
    void pageNumbersShouldStayNumeric() throws Exception {
        PageResult<String> page = PageResult.of(120L, 1L, 20L, List.of("a"));

        String json = OBJECT_MAPPER.writeValueAsString(page);

        assertThat(json).contains("\"total\":120");
        assertThat(json).contains("\"pages\":6");
        assertThat(json).contains("\"current\":1");
        assertThat(json).contains("\"size\":20");
    }

    @Test
    @DisplayName("LocalDateTime 统一为 yyyy-MM-dd HH:mm:ss")
    void localDateTimeShouldBeFormatted() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(
                Map.of("createTime", LocalDateTime.of(2026, 9, 26, 15, 30, 0)));

        assertThat(json).isEqualTo("{\"createTime\":\"2026-09-26 15:30:00\"}");
    }

    @Test
    @DisplayName("反序列化时忽略未知字段 —— 保证 Feign 调用在接口新增字段后不会失败")
    void unknownFieldsShouldBeIgnored() throws Exception {
        PageResult<String> page = OBJECT_MAPPER.readValue(
                "{\"total\":3,\"pages\":1,\"current\":1,\"size\":10,\"records\":[],\"unknownField\":\"x\"}",
                PageResult.class);

        assertThat(page.getTotal()).isEqualTo(3);
    }
}
