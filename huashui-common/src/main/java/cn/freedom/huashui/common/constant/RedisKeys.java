package cn.freedom.huashui.common.constant;

/**
 * Redis key 常量。
 *
 * <p>集中定义的原因：缓存 key 一旦拼错，缓存会「静默失效」——
 * 代码不报错，只是每次都不命中，很难排查。集中定义 + 统一的后缀拼接方法可以避免。
 *
 * @author freedom0213
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 商品详情缓存，String(JSON)，TTL 30~40 分钟（带随机抖动防雪崩） */
    public static final String PRODUCT_DETAIL = "product:detail:";

    /** 商品浏览量计数器，String(自增)，由 XXL-JOB 定时结算回库 */
    public static final String PRODUCT_VIEW = "product:view:";

    /** 有待回写浏览量的商品 id 集合，Set，用于避免全库扫描 */
    public static final String PRODUCT_DIRTY_VIEWS = "product:dirty:views";

    /** 商品分类列表缓存，String(JSON)，变更时删除 */
    public static final String CATEGORY_LIST = "category:list";

    /** 商品锁定分布式互斥锁（辅助手段，主防线是数据库条件更新） */
    public static final String LOCK_PRODUCT = "lock:product:";

    /** 登出 token 黑名单（90% 阶段可选） */
    public static final String AUTH_BLACKLIST = "auth:blacklist:";

    /**
     * 已消费消息标记（MQ 消费幂等去重），String。
     *
     * <p>注意它的定位是「<b>减少无效执行</b>」而不是「保证正确性」：
     * 正确性由消费逻辑自身的幂等性兜底（例如商品解锁是条件更新）。
     * 这个标记只是让重复消息不必再做一次注定无效果的数据库写。
     */
    public static final String MQ_CONSUMED = "mq:consumed:";

    public static String mqConsumed(String msgId) {
        return MQ_CONSUMED + msgId;
    }

    public static String productDetail(Long productId) {
        return PRODUCT_DETAIL + productId;
    }

    public static String productView(Long productId) {
        return PRODUCT_VIEW + productId;
    }

    public static String lockProduct(Long productId) {
        return LOCK_PRODUCT + productId;
    }

    public static String authBlacklist(String jti) {
        return AUTH_BLACKLIST + jti;
    }
}
