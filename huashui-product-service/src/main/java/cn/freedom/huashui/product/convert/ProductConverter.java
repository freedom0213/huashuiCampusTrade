package cn.freedom.huashui.product.convert;

import cn.freedom.huashui.common.enums.ProductCondition;
import cn.freedom.huashui.common.enums.ProductStatus;
import cn.freedom.huashui.product.entity.Product;
import cn.freedom.huashui.product.vo.ProductListVO;

/**
 * 商品实体 → 视图对象的转换。
 *
 * <p><b>抽成独立类而不是留在 Service 的私有方法里</b>：收藏列表也要把商品转成
 * 同样的 {@link ProductListVO}。如果留在 Service 里就得复制一份，
 * 而复制出去的副本会随着字段增减慢慢和原件不一致——这类偏差不会报错，
 * 只表现为「同一个商品在首页和收藏页显示的字段不一样」。
 *
 * <p>用静态方法而非 Spring Bean：转换是无状态的纯函数，注入一个只为调静态逻辑的
 * Bean 没有任何收益。
 *
 * @author freedom0213
 */
public final class ProductConverter {

    private ProductConverter() {
    }

    public static ProductListVO toListVO(Product product) {
        return toListVO(product, false);
    }

    /**
     * 转换成列表卡片。
     *
     * @param includeRejectReason 是否填充审核驳回原因。
     *                            <b>只有「我的发布」应传 true</b>——驳回原因是卖家私事，
     *                            收藏列表 / 公共列表都复用本方法，无条件填充会把它泄露给买家
     */
    public static ProductListVO toListVO(Product product, boolean includeRejectReason) {
        ProductListVO vo = new ProductListVO();
        vo.setId(product.getId());
        vo.setTitle(product.getTitle());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setCoverUrl(product.getCoverUrl());
        vo.setTradePlace(product.getTradePlace());
        vo.setCampus(product.getCampus());
        vo.setConditionLevel(product.getConditionLevel());
        vo.setConditionDesc(descOfCondition(product.getConditionLevel()));
        vo.setStatus(product.getStatus());
        vo.setStatusDesc(descOfStatus(product.getStatus()));
        if (includeRejectReason) {
            vo.setRejectReason(product.getRejectReason());
        }
        vo.setViewCount(product.getViewCount());
        vo.setFavoriteCount(product.getFavoriteCount());
        vo.setPublishTime(product.getPublishTime());
        return vo;
    }

    public static String descOfCondition(Integer code) {
        ProductCondition condition = ProductCondition.of(code);
        return condition == null ? "" : condition.getDesc();
    }

    public static String descOfStatus(Integer code) {
        ProductStatus status = ProductStatus.of(code);
        return status == null ? "" : status.getDesc();
    }
}
