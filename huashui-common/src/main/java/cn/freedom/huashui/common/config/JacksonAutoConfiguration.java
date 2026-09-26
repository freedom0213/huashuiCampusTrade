package cn.freedom.huashui.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一 JSON 序列化规则。
 *
 * <p>解决三个实际问题：
 * <ol>
 *   <li><b>雪花 ID 精度丢失</b>：主键是 19 位雪花 ID（如 2103749049839185921），
 *       而 JS 的 Number 只能安全表示 2^53-1（16 位），
 *       直接以数字返回，前端拿到的会是 2103749049839185900——末几位被抹平。
 *       这种错误不报异常，只表现为「明明传了 id 却查不到数据」，极难排查。
 *       因此所有 Long 统一序列化成字符串，前端把 ID 当字符串传递即可。
 *       副作用是分页的 total 等数值也变成字符串，但接口已返回 pages，
 *       前端不需要自己对总量做算术，影响可忽略。</li>
 *   <li><b>时间格式</b>：JDK8 时间类型默认被序列化成数组（{@code [2026,9,26,14,37,24]}），
 *       前端无法直接使用，统一成 {@code yyyy-MM-dd HH:mm:ss}。</li>
 *   <li><b>接口演进</b>：下游返回的 JSON 多出字段时会反序列化失败（Feign 调用常见），
 *       关闭 FAIL_ON_UNKNOWN_PROPERTIES 让接口演进更平滑。</li>
 * </ol>
 *
 * @author freedom0213
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonAutoConfiguration {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer huashuiJacksonCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));

            // Long / long 一律转字符串，避免雪花 ID 在 JS 端丢精度
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);

            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }
}
