package cn.freedom.huashui.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
 * <p>解决两个实际问题：
 * <ol>
 *   <li>JDK8 时间类型默认被序列化成数组或时间戳（{@code [2026,9,26,14,37,24]}），
 *       前端无法直接使用，这里统一成 {@code yyyy-MM-dd HH:mm:ss}。</li>
 *   <li>下游返回的 JSON 多出字段时会反序列化失败（Feign 调用常见），
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
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }
}
