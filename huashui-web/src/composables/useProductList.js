import { ref } from 'vue'
import { listProducts } from '@/api/product'

/**
 * 商品列表加载（分页 + 触底加载 + 三态）
 *
 * 用法：
 *   const list = useProductList({ campus: '龙子湖' })
 *   onMounted(() => list.load(true))
 *   // 模板里 <div class="page-scroll" @scroll="list.onScroll">
 *
 * 说明：`total` 取自后端 PageResult.total，用来判断「还有没有下一页」，
 * 不靠「本页条数 < size」判断（后者在总数正好整除时会多发一次空请求）。
 */
export function useProductList(baseParams = {}) {
  const items = ref([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(20)
  const loading = ref(false)
  const loadingMore = ref(false)
  const finished = ref(false)
  const error = ref('')
  const loaded = ref(false)

  async function fetchPage(reset) {
    const res = await listProducts({ ...baseParams, page: page.value, size: size.value })
    const records = res.records || []
    total.value = res.total || 0
    items.value = reset ? records : items.value.concat(records)
    finished.value = items.value.length >= total.value || records.length === 0
  }

  /** reset=true 重新从第一页拉（切换筛选条件时用） */
  async function load(reset = false) {
    if (reset) {
      page.value = 1
      items.value = []
      finished.value = false
      error.value = ''
    }
    if (finished.value) return

    if (reset || !loaded.value) {
      loading.value = true
    } else {
      loadingMore.value = true
    }
    try {
      await fetchPage(reset)
      loaded.value = true
      if (!finished.value) page.value += 1
    } catch (e) {
      // 加载更多失败时不把已加载的数据清掉，只提示；首屏失败才进入错误态
      if (reset || !items.value.length) error.value = e.message || '加载失败'
      else finished.value = true
    } finally {
      loading.value = false
      loadingMore.value = false
    }
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || finished.value || !loaded.value) return
    await load(false)
  }

  /** 挂在滚动容器上：距底部 240px 时预加载 */
  function onScroll(e) {
    const el = e.target
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 240) loadMore()
  }

  /** 替换全部筛选条件并重新加载。
      必须是「整体替换」而不是 merge —— 否则去掉某个筛选时旧 key 会残留，
      表现为「取消了校区筛选但结果还是只出龙子湖」。 */
  function setParams(next) {
    Object.keys(baseParams).forEach((k) => delete baseParams[k])
    Object.assign(baseParams, next)
    return load(true)
  }

  return {
    items,
    total,
    loading,
    loadingMore,
    finished,
    error,
    loaded,
    load,
    loadMore,
    onScroll,
    setParams,
    baseParams
  }
}
