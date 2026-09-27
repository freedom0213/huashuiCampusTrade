package cn.freedom.huashui.product.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 缓存读写封装：一次性处理掉缓存穿透、击穿、雪崩三个问题。
 *
 * <p><b>为什么用 {@code StringRedisTemplate} + 自己序列化，而不是
 * {@code RedisTemplate<String, Object>} + {@code GenericJackson2JsonRedisSerializer}：</b>
 * 后者的默认类型化机制会往每个值里塞 {@code @class} 字段，而它与 {@code LocalDateTime}
 * 这类 final 类型的组合有不少坑；一旦缓存里的旧格式数据与新代码不兼容，
 * 报出来的是难以定位的反序列化异常。自己用 ObjectMapper 序列化成纯 JSON 字符串，
 * 好处是 ① 类型由调用方显式给出，不依赖隐式的类型信息；
 * ② {@code redis-cli get} 直接就能看懂内容，排查方便。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheService {

    /** 缓存空值的占位符。存它而不是不存，是为了区分「查过了、确实没有」与「还没查过」 */
    private static final String NULL_PLACEHOLDER = "\"__NULL__\"";

    /** 空值缓存的 TTL。很短，因为它的作用是「防刷」而不是「加速」 */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    /** 缓存重建互斥锁的 key 前缀 */
    private static final String LOCK_PREFIX = "lock:cache:rebuild:";

    /**
     * 锁的自动过期时间。必须有 —— 持锁线程若在回源途中断电或被杀，
     * 没有过期时间的锁会永远留在那里，之后所有请求都抢不到锁。
     */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /** 没抢到锁时的自旋次数与间隔：5 × 50ms = 250ms 上限 */
    private static final int SPIN_MAX_TIMES = 5;
    private static final long SPIN_INTERVAL_MS = 50L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 读缓存；未命中则回源并回填。适用于非泛型类型。
     *
     * @see #getOrLoad(String, JavaType, Duration, Supplier)
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return getOrLoad(key, objectMapper.getTypeFactory().constructType(type), ttl, loader);
    }

    /**
     * 读缓存；未命中则回源并回填。适用于 {@code List<XxxVO>} 这类泛型类型。
     *
     * <p>为什么需要这个重载：{@code Class} 参数无法承载泛型信息，
     * 传 {@code List.class} 反序列化回来的是 {@code List<LinkedHashMap>}，
     * 遍历时取字段就会抛 {@code ClassCastException} —— 而且是运行时才炸。
     * 正确做法是传入用 {@code TypeFactory} 构造出的完整类型。
     *
     * <p>把「读缓存 → 抢锁 → 回源 → 回填」整条链路封在这里，调用方只需提供
     * 「怎么查数据库」这一件事（{@code loader}）。这样调用方不必关心缓存三态
     * （命中 / 未命中 / 命中空值），也就不会漏掉任何一条分支。
     *
     * @param key    缓存 key
     * @param type   完整的目标类型
     * @param ttl    基础 TTL（实际写入时会加随机抖动）
     * @param loader 回源逻辑，<b>返回 null 表示数据确实不存在</b>（会被缓存成空值）
     */
    public <T> T getOrLoad(String key, JavaType type, Duration ttl, Supplier<T> loader) {
        // ① 读缓存
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_PLACEHOLDER.equals(cached)) {
                // 命中空值：之前查过，库里确实没有。直接返回，不再查库 —— 这就是防穿透
                return null;
            }
            T value = deserialize(cached, type);
            if (value != null) {
                return value;
            }
            // 反序列化失败（多见于缓存结构升级后残留的旧格式数据）：
            // 当作未命中并删掉，避免这个 key 一直报错
            log.warn("缓存反序列化失败，按未命中处理 | key={}", key);
            redisTemplate.delete(key);
        }

        // ② 未命中 → 抢互斥锁。同一时刻只放一个线程去回源，其余线程短暂等待，
        //    避免热点 key 刚过期时大批请求同时打到数据库（缓存击穿）
        String lockKey = LOCK_PREFIX + key;
        if (!tryLock(lockKey)) {
            return waitForRebuild(key, type, loader);
        }

        // ③ 抢到锁 → 回源 + 回填
        try {
            T value = loader.get();
            if (value == null) {
                // 防穿透：把「不存在」也缓存起来。不缓存的话，
                // 反复请求一个不存在的 id 每次都会打到数据库
                redisTemplate.opsForValue().set(key, NULL_PLACEHOLDER, NULL_TTL);
            } else {
                redisTemplate.opsForValue().set(key, serialize(value), withJitter(ttl));
            }
            return value;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /** 删除缓存 */
    public void delete(String key) {
        if (key == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            // 删缓存失败不该让业务流程失败：最坏结果是缓存里多留一份旧数据一阵子（到期自愈）
            log.error("删除缓存失败 | key={}", key, e);
        }
    }

    /** 批量删除缓存 */
    public void delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("批量删除缓存失败 | keys={}", keys, e);
        }
    }

    /**
     * <b>事务提交之后</b>再删除缓存。
     *
     * <p>为什么必须等提交后：如果在事务提交前删，另一个线程可能在新数据还没有落库时
     * 就读到旧值并写回缓存；随后事务提交，数据库是新的、缓存却是旧的 ——
     * 而且这份旧缓存会一直留到过期，形成「改了但页面没变」的长期不一致。
     *
     * <p>至于「先更新数据库再删缓存」这个顺序本身：删比更新可靠。
     * 并发更新时「更新缓存」会把旧值写进去，而「删缓存」最坏只是多一次回源。
     */
    public void deleteAfterCommit(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(keys);
                }
            });
        } else {
            // 没有活动事务（例如只有一条 UPDATE 的写操作），直接删即可
            delete(keys);
        }
    }

    /**
     * 构造 {@code List<T>} 的完整类型，供 {@link #getOrLoad(String, JavaType, Duration, Supplier)} 使用。
     *
     * <p>把它放在这里而不是让调用方自己拼 {@code TypeFactory}，
     * 是为了让「缓存 List 型数据」这件事只有一个入口，不容易写错。
     */
    public <T> JavaType listType(Class<T> elementType) {
        return objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
    }

    /**
     * 尝试标记「这条消息已处理」，用于 MQ 消费幂等。
     *
     * <p>它的定位是<b>减少无效执行</b>，而不是保证正确性 ——
     * 正确性由消费逻辑自身的幂等性兜底（商品解锁是条件更新，重复执行无副作用）。
     *
     * @param key 去重标记的 key
     * @param ttl 标记保留时长，应长于消息可能的重投窗口
     * @return {@code true} 表示标记成功（第一次见到）；{@code false} 表示之前已处理过
     */
    public boolean tryMarkProcessed(String key, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 撤销「已处理」标记。
     *
     * <p><b>这一步在消费失败时不能省。</b>
     * 「先标记、再执行业务」这个顺序天然有风险：一旦业务执行失败、标记却留下了，
     * 消息重投时会被判成「已处理」直接跳过 ——
     * <b>消息被永久丢弃，而且不报任何错</b>。所以业务失败必须把标记删掉，
     * 让重投能真正重跑一遍业务。
     */
    public void clearMark(String key) {
        delete(key);
    }

    /** 查看 key 是否存在（调试与测试用） */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 自增计数并返回自增后的值。
     *
     * <p>浏览量用它而不是 {@code UPDATE view_count = view_count + 1}：
     * 后者会让每次浏览都对同一行加排他锁，热门商品上形成行锁热点、请求排队；
     * Redis 自增是内存操作，没有锁竞争。
     */
    public long increment(String key) {
        Long value = redisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
    }

    /**
     * 把一个元素加入集合。
     *
     * <p>浏览量自增的同时把商品 id 记进「待结算」集合，
     * 定时结算任务据此扫描，避免为找出哪些商品有增量而全表扫描。
     */
    public void addToSet(String key, String value) {
        redisTemplate.opsForSet().add(key, value);
    }

    /**
     * 读取集合成员（最多 {@code limit} 个）。
     *
     * <p>用 {@code SMEMBERS} 全量取再截断，而不是 {@code SSCAN} 游标：
     * 「待结算」集合的规模受「被访问过的商品数」限制，校园项目的量级下完全可控；
     * 换成游标会让调用方多处理一遍遍历状态。若将来访问量级上来了，此处应改为 SSCAN。
     */
    public Set<String> members(String key, int limit) {
        Set<String> all = redisTemplate.opsForSet().members(key);
        if (all == null || all.isEmpty()) {
            return Collections.emptySet();
        }
        if (all.size() <= limit) {
            return all;
        }
        return all.stream().limit(limit).collect(Collectors.toSet());
    }

    /** 从集合中移除一个成员（结算完成后调用，避免同一个商品每轮都被重复处理） */
    public void removeFromSet(String key, String value) {
        redisTemplate.opsForSet().remove(key, value);
    }

    /**
     * <b>原子地</b>把计数器取走并置零，返回取走前的值（Redis 的 {@code GETSET}）。
     *
     * <p>浏览量结算必须用它，不能用「先 get 再 set 0」：
     * 两步之间到达的浏览会被后一步清零抹掉，而这属于**静默丢数据**。
     * {@code GETSET} 是单条原子命令，取走之后的新增量会落在新的 0 上，一个都不会丢。
     *
     * @return 取走前的计数值；key 不存在时返回 0
     */
    public long getAndReset(String key) {
        String previous = redisTemplate.opsForValue().getAndSet(key, "0");
        if (previous == null || previous.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(previous);
        } catch (NumberFormatException e) {
            log.warn("浏览量计数器的值不是数字，按 0 处理 | key={} | value={}", key, previous);
            return 0L;
        }
    }

    /**
     * 把计数器的值加上指定数量。
     *
     * <p>用于结算失败时把已取走的差值<b>还回去</b> —— 否则这批浏览量就永久丢失了。
     * 不能写成「读出来再写回去」（并发下会覆盖别人的增量），Redis 的 {@code INCRBY} 是原子的。
     */
    public void incrementBy(String key, long delta) {
        redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 重新封装 TTL：加最多 1/3 的随机抖动，避免大批 key 同时过期造成缓存雪崩 */
    private Duration withJitter(Duration base) {
        long extra = ThreadLocalRandom.current().nextLong(Math.max(base.toSeconds() / 3, 1L));
        return base.plusSeconds(extra);
    }

    private boolean tryLock(String lockKey) {
        // SETNX + 过期时间：这两个动作在 Redis 里是原子的，不会出现
        // 「设置成功但过期时间没设上」导致死锁的情况
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 没抢到锁时的等待策略：短暂自旋等持锁线程把缓存写好，而不是所有线程一起回源。
     *
     * <p>自旋超时后<b>退化为直查数据库但不写缓存</b>：
     * 不写是为了不和持锁线程抢着回填（避免竞态写入旧值），
     * 直查是为了不让这个用户因为别人拿锁而多等更久 —— 用户体验优先于「绝对只查一次库」。
     */
    private <T> T waitForRebuild(String key, JavaType type, Supplier<T> loader) {
        for (int i = 0; i < SPIN_MAX_TIMES; i++) {
            sleepQuietly();
            String retry = redisTemplate.opsForValue().get(key);
            if (retry != null) {
                return NULL_PLACEHOLDER.equals(retry) ? null : deserialize(retry, type);
            }
        }
        log.warn("等待缓存重建超时，本次退化为直查数据库 | key={}", key);
        return loader.get();
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(SPIN_INTERVAL_MS);
        } catch (InterruptedException e) {
            // 恢复中断标记，让上层能感知到线程被要求退出
            Thread.currentThread().interrupt();
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("缓存序列化失败", e);
        }
    }

    private <T> T deserialize(String json, JavaType type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("缓存反序列化失败 | type={}", type, e);
            return null;
        }
    }
}
