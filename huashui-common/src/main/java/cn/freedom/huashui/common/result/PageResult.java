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
 * <p><b>为什么计数字段用 {@code int} 而不是 {@code long}：</b>
 * huashui-common 里配置了「Long 一律序列化为字符串」（用于解决雪花 ID 在 JS 端丢精度的问题）。
 * 如果这里用 {@code long}，分页的 total / pages 也会变成字符串 {@code "120"}，
 * 前端若做 {@code total > 100} 这类比较，会退化成字符串比较而得到错误结果。
 * <p>因此约定：<b>Long 在本项目里只用于 ID（一律转字符串），数量类字段用 int（保持数字）。</b>
 * 校园平台的商品量不可能超过 int 上限，转换用 {@link Math#toIntExact}，
 * 保证溢出时立刻抛错，而不是悄悄得到一个负数。
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
    private int total;

    /** 总页数 */
    private int pages;

    /** 当前页码，从 1 开始 */
    private int current;

    /** 每页条数 */
    private int size;

    /** 当前页数据 */
    private List<T> records;

    /**
     * 构造分页结果，自动计算总页数。
     *
     * <p>入参保持 {@code long}，方便调用方直接把 MyBatis-Plus {@code IPage} 的值传进来，
     * 由本方法统一做范围校验。
     */
    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        int safeTotal = Math.toIntExact(total);
        int safeCurrent = Math.toIntExact(current);
        int safeSize = Math.toIntExact(size);
        int pages = safeSize <= 0 ? 0 : (safeTotal + safeSize - 1) / safeSize;
        return new PageResult<>(safeTotal, pages, safeCurrent, safeSize,
                records == null ? Collections.emptyList() : records);
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(0, 0, 1, 0, Collections.emptyList());
    }
}
