package cn.freedom.huashui.product.service;

import cn.freedom.huashui.product.vo.CategoryVO;

import java.util.List;

/**
 * 分类服务。
 *
 * <p>阶段 5 只做「取启用中的分类列表」——这是前端首页胶囊行、搜索筛选、发布页下拉的数据源。
 * 分类的增删改属于管理端能力，放在阶段 12。
 *
 * @author freedom0213
 */
public interface CategoryService {

    /**
     * 查询启用中的分类，按 sort 升序。
     */
    List<CategoryVO> listEnabled();
}
