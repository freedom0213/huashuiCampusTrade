package cn.freedom.huashui.user.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 *
 * @author freedom0213
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件。
     *
     * <p><b>必须注册，否则 {@code Page} 查询不会真正分页</b>：
     * Mapper 依然会返回第一页数据，但 total 恒为 0，
     * 而且 SQL 里没有 LIMIT——数据量上来后会全表扫描。这个坑不会报错，只会「数据不对」。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充创建时间 / 更新时间。
     *
     * <p>与数据库的 {@code DEFAULT CURRENT_TIMESTAMP} 二选一即可。
     * 这里选应用层填充，好处是插入后实体对象里立刻就能拿到时间值，
     * 不需要再查一次数据库。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
