package cn.freedom.huashui.product.service.impl;

import cn.freedom.huashui.product.entity.Category;
import cn.freedom.huashui.product.mapper.CategoryMapper;
import cn.freedom.huashui.product.service.CategoryService;
import cn.freedom.huashui.product.vo.CategoryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryVO> listEnabled() {
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
