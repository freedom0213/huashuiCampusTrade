package cn.freedom.huashui.product.service.impl;

import cn.freedom.huashui.common.constant.RedisKeys;
import cn.freedom.huashui.product.cache.CacheService;
import cn.freedom.huashui.product.entity.Category;
import cn.freedom.huashui.product.mapper.CategoryMapper;
import cn.freedom.huashui.product.service.CategoryService;
import cn.freedom.huashui.product.vo.CategoryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 分类服务实现。
 *
 * @author freedom0213
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /**
     * 分类列表缓存 TTL。
     *
     * <p>比商品详情长得多：分类是「几乎不变、人人要读」的数据 ——
     * 首页胶囊行、搜索筛选、发布页下拉都要它，而它一年可能只改几次。
     * 这是全站性价比最高的一处缓存。
     */
    private static final Duration CATEGORY_TTL = Duration.ofHours(1);

    private final CategoryMapper categoryMapper;
    private final CacheService cacheService;

    @Override
    public List<CategoryVO> listEnabled() {
        // 注意泛型：必须用 constructCollectionType 构造 List<CategoryVO> 的完整类型，
        // 传 List.class 反序列化回来的是 List<LinkedHashMap>，遍历取字段时才炸
        return cacheService.getOrLoad(
                RedisKeys.CATEGORY_LIST,
                cacheService.listType(CategoryVO.class),
                CATEGORY_TTL,
                this::loadFromDb);
    }

    private List<CategoryVO> loadFromDb() {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, STATUS_ENABLED)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));

        return categories.stream().map(this::toVO).toList();
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        return vo;
    }
}
