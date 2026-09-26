package cn.freedom.huashui.product.service;

import cn.freedom.huashui.common.result.PageResult;
import cn.freedom.huashui.product.dto.FavoriteQueryDTO;
import cn.freedom.huashui.product.vo.ProductListVO;

/**
 * 收藏服务。
 *
 * <p>收藏归属商品域：它是「用户与商品之间的关系」，放在商品域不需要跨服务查询，
 * 也避免用户域反向依赖商品域。
 *
 * @author freedom0213
 */
public interface FavoriteService {

    /**
     * 收藏商品。
     *
     * @throws cn.freedom.huashui.common.exception.BizException
     *         商品不存在 / 不能收藏自己发布的商品 / 已经收藏过
     */
    void add(Long productId);

    /**
     * 取消收藏。
     *
     * @throws cn.freedom.huashui.common.exception.BizException 尚未收藏过该商品
     */
    void remove(Long productId);

    /**
     * 我的收藏，按收藏时间倒序。
     *
     * <p><b>已下架 / 已售出的商品仍然返回</b>，并在每一项里带上商品当前状态，
     * 由前端标注「已售出 / 已下架」。直接过滤掉的话，用户会以为收藏丢了。
     */
    PageResult<ProductListVO> listMine(FavoriteQueryDTO query);
}
