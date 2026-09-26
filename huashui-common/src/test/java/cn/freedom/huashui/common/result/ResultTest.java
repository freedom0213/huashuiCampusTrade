package cn.freedom.huashui.common.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一响应体与分页结果测试。
 *
 * @author freedom0213
 */
class ResultTest {

    @Test
    @DisplayName("success 携带数据并标记成功")
    void successCarriesData() {
        Result<String> result = Result.success("hello");

        assertEquals(200, result.getCode());
        assertEquals("hello", result.getData());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("error 使用错误码枚举的默认文案")
    void errorUsesEnumMessage() {
        Result<Void> result = Result.error(ResultCode.PRODUCT_LOCK_FAILED);

        assertEquals(30002, result.getCode());
        assertEquals("手慢了，商品已被别人抢先买走", result.getMessage());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("分页结果自动计算总页数")
    void pageResultCalculatesPages() {
        PageResult<String> page = PageResult.of(25L, 2L, 10L, List.of("a", "b"));

        assertEquals(25L, page.getTotal());
        assertEquals(3L, page.getPages());
        assertEquals(2L, page.getCurrent());
        assertEquals(2, page.getRecords().size());
    }

    @Test
    @DisplayName("空数据时不会出现除零或 null 集合")
    void pageResultHandlesEmptyAndZeroSize() {
        assertEquals(0L, PageResult.of(0L, 1L, 10L, null).getPages());
        assertTrue(PageResult.of(0L, 1L, 10L, null).getRecords().isEmpty());
        assertEquals(0L, PageResult.of(10L, 1L, 0L, null).getPages());
        assertTrue(PageResult.empty().getRecords().isEmpty());
    }
}
