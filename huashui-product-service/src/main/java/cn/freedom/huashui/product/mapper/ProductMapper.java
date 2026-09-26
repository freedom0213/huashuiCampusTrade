package cn.freedom.huashui.product.mapper;

import cn.freedom.huashui.product.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 Mapper。
 *
 * <p>阶段 5 的查询都能用条件构造器表达，因此暂不写 XML。
 * 将来出现多表关联或复杂统计（如「按分类统计在售商品数」）时再补 XML。
 *
 * @author freedom0213
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
