package cn.freedom.huashui.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应体。
 *
 * <p>刻意不依赖 MyBatis-Plus 的 {@code IPage}：common 模块应保持与 ORM 框架解耦，
 * 各服务把 {@code IPage} 转成本类即可。
 *
 * @param <T> 记录类型
 * @author freedom0213
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long pages;

    /** 当前页码，从 1 开始 */
    private long current;

    /** 每页条数 */
    private long size;

    /** 当前页数据 */
    private List<T> records;

    /**
     * 构造分页结果，自动计算总页数。
     */
    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(total, pages, current, size,
                records == null ? Collections.emptyList() : records);
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(0L, 0L, 1L, 0L, Collections.emptyList());
    }
}
