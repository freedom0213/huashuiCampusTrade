<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import OptionSheet from '@/components/OptionSheet.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import { listCategories } from '@/api/category'
import { getProductDetail, publishProduct, updateProduct } from '@/api/product'
import { uploadImages } from '@/api/file'
import {
  CAMPUS_LIST,
  CAMPUS_PLACES,
  CONDITION_OPTIONS,
  IMAGE_ACCEPT,
  MAX_IMAGE_COUNT,
  buildTradePlace
} from '@/constants/enums'
import { toastFromError, toastOk } from '@/composables/useToast'

/* ==========================================================
   发布 / 编辑商品 —— /publish 与 /publish/:id 复用同一页面
   接口（见 docs/03-前端接口核对清单.md）：
     POST /api/product        ProductSaveDTO → 新商品 id
     PUT  /api/product/{id}   path + ProductSaveDTO
     POST /api/file/upload    multipart → { url }
     GET  /api/product/detail/{id}  编辑态回填

   🔴 两处刻意比后端更严（产品要求，不是笔误）：
   ① 标题 30 字 / 描述 500 字 —— 后端上限是 64 / 1000（更宽松），前端按设计稿收紧；
   ② 图片至少 1 张 —— 后端 imageUrls 是选填，但二手交易没图没人敢买。

   🔴 提交成功**不能说「发布成功」**：商品要经过管理员审核才上架，
      文案必须是「已提交审核」，否则用户会以为已经能看到了。

   编辑态被驳回的商品可以在这里改完重提，状态回到 PENDING_AUDIT。
   ========================================================== */

const route = useRoute()
const router = useRouter()

const editId = computed(() => String(route.params.id || ''))
const isEdit = computed(() => !!editId.value)

const form = ref({
  title: '',
  description: '',
  categoryId: '',
  price: '',
  originalPrice: '',
  conditionLevel: 1, // 默认「九成新」：校园二手最常见的档位
  campus: '',
  place: ''
})

const images = ref([])
const categories = ref([])
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const error = ref('')
const errors = ref({})

/* 浮层：'' | 'category' | 'campus' | 'place' | 'image' */
const sheet = ref('')
const activeImage = ref(-1)
const fileEl = ref(null)

const placeOptions = computed(() => {
  const list = CAMPUS_PLACES[form.value.campus] || []
  // 「仅到校区」放最前：只要校区也完全可用（tradePlace 就存校区名）
  return [{ value: '', label: '仅到校区（不指定地标）' }].concat(
    list.map((p) => ({ value: p, label: p }))
  )
})

const categoryOptions = computed(() =>
  categories.value.map((c) => ({ value: String(c.id), label: c.name }))
)
const campusOptions = computed(() => CAMPUS_LIST.map((c) => ({ value: c, label: `${c}校区` })))

const categoryName = computed(
  () => categories.value.find((c) => String(c.id) === form.value.categoryId)?.name || ''
)
const tradePlaceText = computed(() => buildTradePlace(form.value.campus, form.value.place))

const canSubmit = computed(
  () =>
    !submitting.value &&
    images.value.length >= 1 &&
    form.value.title.trim() !== '' &&
    form.value.description.trim() !== '' &&
    form.value.categoryId !== '' &&
    Number(form.value.price) > 0 &&
    form.value.conditionLevel !== null &&
    form.value.campus !== ''
)

/* ── 加载 ── */
onMounted(async () => {
  loading.value = true
  try {
    const [cats] = await Promise.all([
      listCategories(),
      isEdit.value ? loadForEdit() : Promise.resolve()
    ])
    categories.value = cats || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
})

async function loadForEdit() {
  const d = await getProductDetail(editId.value)
  form.value = {
    title: d.title || '',
    description: d.description || '',
    categoryId: d.categoryId ? String(d.categoryId) : '',
    price: d.price != null ? String(d.price) : '',
    originalPrice: d.originalPrice != null ? String(d.originalPrice) : '',
    conditionLevel: d.conditionLevel ?? 1,
    campus: d.campus || ''
  }
  // 地标从快照文本反解：tradePlace 形如「龙子湖 · 第二食堂」
  const parts = String(d.tradePlace || '')
    .split('·')
    .map((s) => s.trim())
    .filter(Boolean)
  form.value.place = parts.length > 1 ? parts[1] : ''
  images.value = Array.isArray(d.imageUrls) ? d.imageUrls.filter(Boolean) : []
}

/* ── 图片 ── */
function pickFile() {
  if (images.value.length >= MAX_IMAGE_COUNT) {
    return toastOk('最多 9 张图', '长按已上传的图片可以删除或设为封面')
  }
  fileEl.value?.click()
}

async function onFiles(e) {
  const files = [...(e.target.files || [])]
  e.target.value = '' // 允许重复选同一张
  if (!files.length) return

  const room = MAX_IMAGE_COUNT - images.value.length
  if (files.length > room) {
    toastOk(`最多 ${MAX_IMAGE_COUNT} 张`, `本次只上传前 ${room} 张`)
  }
  const picked = files.slice(0, room)

  uploading.value = true
  try {
    const urls = await uploadImages(picked)
    images.value = images.value.concat(urls)
  } catch (err) {
    // ⚠️ 这里是**异常**（不是成功），必须带上失败原因
    toastFromError(err, '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function openImageSheet(i) {
  activeImage.value = i
  sheet.value = 'image'
}

function setAsCover() {
  const i = activeImage.value
  if (i > 0) {
    const [img] = images.value.splice(i, 1)
    images.value.unshift(img)
  }
  sheet.value = ''
}

function removeImage() {
  images.value.splice(activeImage.value, 1)
  sheet.value = ''
}

/* ── 表单校验（前端只拦低级错误，最终以后端为准）── */
function validate() {
  const e = {}
  const t = form.value.title.trim()
  const d = form.value.description.trim()
  if (!images.value.length) e.images = '至少上传 1 张图片'
  if (!t) e.title = '请填写标题'
  else if (t.length > 30) e.title = '标题最长 30 字'
  if (!d) e.description = '请填写描述'
  if (!form.value.categoryId) e.categoryId = '请选择分类'
  if (!(Number(form.value.price) > 0)) e.price = '请填写大于 0 的售价'
  if (form.value.originalPrice !== '') {
    const op = Number(form.value.originalPrice)
    if (!(op > 0)) e.originalPrice = '原价需大于 0'
    else if (op <= Number(form.value.price)) e.originalPrice = '原价需高于售价（否则不显示划线价）'
  }
  if (!form.value.campus) e.tradePlace = '请选择交易地点'
  errors.value = e
  return Object.keys(e).length === 0
}

function clearError(k) {
  if (errors.value[k]) errors.value = { ...errors.value, [k]: '' }
}

async function submit() {
  if (submitting.value) return
  if (!validate()) {
    const first = Object.values(errors.value).find(Boolean)
    return toastFromError(new Error(first), '还差一点')
  }
  submitting.value = true
  try {
    const payload = {
      categoryId: form.value.categoryId,
      title: form.value.title.trim(),
      description: form.value.description.trim(),
      price: Number(form.value.price),
      originalPrice: form.value.originalPrice === '' ? null : Number(form.value.originalPrice),
      campus: form.value.campus,
      tradePlace: tradePlaceText.value,
      conditionLevel: form.value.conditionLevel,
      imageUrls: images.value
    }
    if (isEdit.value) {
      await updateProduct(editId.value, payload)
      toastOk('已重新提交审核', '管理员将重新审核该商品')
    } else {
      await publishProduct(payload)
      toastOk('已提交审核', '管理员审核通过后将自动上架')
    }
    router.replace('/user/products')
  } catch (e) {
    toastFromError(e, isEdit.value ? '保存失败' : '发布失败')
  } finally {
    submitting.value = false
  }
}

function cancel() {
  if (window.history.state?.back) router.back()
  else router.replace('/mine')
}

/* ── 交易地点的两级联动 ──
   选完校区紧接着弹地标选择，少一次点击；地标可选「仅到校区」。

   🔴 这里必须处理 OptionSheet 的事件顺序：它的 pick() 是**先 emit update:modelValue、
      再 emit close**。如果 close 直接写成 `sheet = ''`，会把刚设好的 `'place'` 又清掉
      —— 实测二级浮层根本不弹出。所以用一个标志忽略「由选中动作引发的那次 close」。
      （不能改用 nextTick 延后打开：中间会渲染出关闭态，浮层会闪一下。）

   ⚠️ 这两个函数必须定义在 script setup 里 —— 放到额外的 <script>（Options API）
      里写 this.form 会访问不到 setup 作用域的绑定，运行时直接报错。 */
let ignoreNextClose = false

function onPickCampus(v) {
  form.value.campus = v
  form.value.place = ''
  clearError('tradePlace')
  ignoreNextClose = true
  sheet.value = 'place'
}

function onPickPlace(v) {
  form.value.place = v
  sheet.value = ''
}

/** 所有浮层共用的关闭处理 */
function closeSheet() {
  if (ignoreNextClose) {
    ignoreNextClose = false
    return
  }
  sheet.value = ''
}
</script>

<template>
  <div class="page">
    <header class="pubnav">
      <button class="press" @click="cancel">取消</button>
      <b>{{ isEdit ? '编辑商品' : '发布商品' }}</b>
      <span class="ph" />
    </header>

    <div class="page-scroll">
      <template v-if="loading">
        <div class="sk-block" />
        <div class="sk-form" />
      </template>

      <div v-else-if="error" class="state">
        <p class="msg">{{ error }}</p>
        <button class="retry press" @click="$router.go(0)">重试</button>
      </div>

      <template v-else>
        <!-- 图片九宫格：首图即封面 -->
        <div class="uploader">
          <button
            v-for="(u, i) in images"
            :key="u + i"
            class="box pic press"
            @click="openImageSheet(i)"
          >
            <img :src="u" alt="" />
            <span v-if="i === 0" class="cover">封面</span>
          </button>
          <button v-if="images.length < MAX_IMAGE_COUNT" class="box add press" :disabled="uploading" @click="pickFile">
            <svg v-if="!uploading" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round">
              <path d="M12 6v12M6 12h12" />
            </svg>
            <svg v-else class="spin" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M12 3.5a8.5 8.5 0 108.5 8.5" />
            </svg>
            <span>{{ uploading ? '上传中' : `${images.length}/${MAX_IMAGE_COUNT}` }}</span>
          </button>
        </div>
        <input ref="fileEl" type="file" :accept="IMAGE_ACCEPT" multiple hidden @change="onFiles" />
        <p class="hint">
          第一张为封面图，建议正面无遮挡、光线充足<span v-if="errors.images"> · <i class="err">{{ errors.images }}</i></span>
        </p>

        <!-- 表单单卡片：label 在左、输入在右 -->
        <div class="form">
          <div class="field" :class="{ bad: errors.title }">
            <label>标题</label>
            <input
              v-model="form.title"
              maxlength="30"
              placeholder="如：iPad Air 5 64G 深空灰"
              @input="clearError('title')"
            />
            <span class="count">{{ form.title.length }}/30</span>
          </div>

          <div class="field col" :class="{ bad: errors.description }">
            <label>描述<span class="count-inline">{{ form.description.length }}/500</span></label>
            <textarea
              v-model="form.description"
              maxlength="500"
              rows="3"
              placeholder="补充使用时长、成色细节、配件情况"
              @input="clearError('description')"
            />
          </div>

          <button class="field press" :class="{ bad: errors.categoryId }" @click="sheet = 'category'">
            <label>分类</label>
            <span class="val" :class="{ ph: !categoryName }">
              {{ categoryName || '请选择分类' }}
              <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="#D1D1D6" stroke-width="2" stroke-linecap="round"><path d="M7.8 4.6L13.2 10l-5.4 5.4" /></svg>
            </span>
          </button>

          <div class="field" :class="{ bad: errors.price || errors.originalPrice }">
            <label>售价</label>
            <input
              v-model="form.price"
              class="mini"
              type="number"
              inputmode="decimal"
              min="0"
              placeholder="0"
              @input="clearError('price')"
            />
            <label class="sub">原价</label>
            <input
              v-model="form.originalPrice"
              class="mini"
              type="number"
              inputmode="decimal"
              min="0"
              placeholder="选填"
              @input="clearError('originalPrice')"
            />
          </div>

          <div class="field col">
            <label>成色</label>
            <div class="seg">
              <span
                v-for="o in CONDITION_OPTIONS"
                :key="o.code"
                :class="{ on: form.conditionLevel === o.code }"
                @click="form.conditionLevel = o.code"
              >
                {{ o.label }}
              </span>
            </div>
          </div>

          <button class="field press" :class="{ bad: errors.tradePlace }" @click="sheet = 'campus'">
            <label>交易地点</label>
            <span class="val" :class="{ ph: !tradePlaceText }">
              {{ tradePlaceText || '请选择校区' }}
              <svg width="14" height="14" viewBox="0 0 20 20" fill="none" stroke="#D1D1D6" stroke-width="2" stroke-linecap="round"><path d="M7.8 4.6L13.2 10l-5.4 5.4" /></svg>
            </span>
          </button>
        </div>

        <p v-if="Object.values(errors).some(Boolean)" class="errbox">
          {{ Object.values(errors).find(Boolean) }}
        </p>
        <div class="tail-space" />
      </template>
    </div>

    <div class="pubfoot">
      <button class="btn-main" :disabled="!canSubmit" @click="submit">
        {{ submitting ? '提交中…' : '提交审核' }}
      </button>
      <p>提交后需管理员审核通过才会上架</p>
    </div>

    <!-- 分类 -->
    <OptionSheet
      :visible="sheet === 'category'"
      title="选择分类"
      :options="categoryOptions"
      :model-value="form.categoryId"
      @update:model-value="(v) => ((form.categoryId = v), clearError('categoryId'))"
      @close="closeSheet"
    />
    <!-- 交易地点第一级：校区 -->
    <OptionSheet
      :visible="sheet === 'campus'"
      title="选择校区"
      :options="campusOptions"
      :model-value="form.campus"
      @update:model-value="onPickCampus"
      @close="closeSheet"
    />
    <!-- 交易地点第二级：地标 -->
    <OptionSheet
      :visible="sheet === 'place'"
      :title="`${form.campus}校区 · 选择地标`"
      :options="placeOptions"
      :model-value="form.place"
      @update:model-value="onPickPlace"
      @close="closeSheet"
    />
    <!-- 图片操作 -->
    <ActionSheet :visible="sheet === 'image'" @close="closeSheet">
      <h3>图片</h3>
      <div class="imgact">
        <button class="bg" :disabled="activeImage === 0" @click="setAsCover">设为封面</button>
        <button class="bg danger" @click="removeImage">删除图片</button>
      </div>
      <p class="tip">第一张即封面，会出现在首页与搜索结果里。</p>
    </ActionSheet>
  </div>
</template>



<style scoped>
/* ── 顶栏 ── */
.pubnav {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(var(--safe-top) + 4px) 12px 4px;
  height: calc(50px + var(--safe-top));
}
.pubnav button {
  font-size: 14.5px;
  color: var(--text-2);
  padding: 8px;
}
.pubnav b {
  font-size: 15.5px;
  font-weight: 600;
}
.pubnav .ph {
  width: 34px;
}

/* ── 九宫格 ── */
.uploader {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 9px;
  padding: 6px 16px 0;
}
.box {
  aspect-ratio: 1 / 1;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-3);
  gap: 2px;
  box-shadow: 0 1px 4px rgba(28, 28, 30, 0.03);
  position: relative;
  overflow: hidden;
}
.box.add {
  flex-direction: column;
  border: 1.2px dashed #e6c8d6;
  background: #fffbfc;
  color: var(--pink);
}
.box.add span {
  font-size: 11px;
  color: var(--text-3);
}
/* 封面上的角标 */
.box .cover {
  position: absolute;
  left: 0;
  bottom: 0;
  padding: 2px 7px;
  border-radius: 0 8px 0 8px;
  background: var(--grad-btn);
  color: #fff;
  font-size: 10px;
}
.box.pic img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.spin {
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.hint {
  font-size: 11.5px;
  color: var(--text-2);
  padding: 9px 16px 0;
}
.hint .err {
  color: var(--red);
  font-style: normal;
}

/* ── 表单卡片 ── */
.form {
  margin: 20px 16px 0;
  background: #fff;
  border-radius: 14px;
  padding: 0 14px;
  box-shadow: var(--sh-card);
}
.field {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 52px;
  border-bottom: 1px solid var(--line);
  text-align: left;
}
.field:last-child {
  border-bottom: none;
}
.field label {
  flex: 0 0 54px;
  font-size: 13.5px;
  color: #3a3a3c;
}
.field label.sub {
  flex: 0 0 auto;
  margin-left: 4px;
  font-size: 12.5px;
  color: var(--text-2);
}
.field input,
.field textarea {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: none;
  font-size: 13.5px;
  font-family: inherit;
  color: var(--text);
  resize: none;
}
.field input::placeholder,
.field textarea::placeholder {
  color: var(--text-3);
}
.field .mini {
  flex: 0 0 78px;
  font-size: 15px;
  font-weight: 600;
  color: var(--pink);
}
.field .mini::placeholder {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--text-3);
}
.field .count {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}
.field .count-inline {
  margin-left: 6px;
  font-size: 11px;
  color: var(--text-3);
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}
.field .val {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 13.5px;
  color: #3a3a3c;
}
.field .val.ph {
  color: var(--text-3);
}
.field.col {
  display: block;
  padding: 13px 0;
}
.field.col label {
  display: block;
  margin-bottom: 9px;
}
.field.bad {
  border-bottom-color: var(--red);
}

/* 成色分段器 */
.seg {
  display: flex;
  gap: 6px;
}
.seg span {
  flex: 1;
  height: 34px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--field);
  font-size: 11.5px;
  color: #5a5a5e;
  cursor: pointer;
}
.seg span.on {
  background: var(--grad-btn);
  color: #fff;
  font-weight: 500;
  box-shadow: 0 3px 12px rgba(236, 110, 156, 0.26);
}

.errbox {
  margin: 12px 16px 0;
  font-size: 11.5px;
  color: var(--red);
}
.tail-space {
  height: 20px;
}

/* ── 底部提交栏 ── */
.pubfoot {
  flex: 0 0 auto;
  padding: 14px 16px calc(14px + var(--safe-bottom));
  background: rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  box-shadow: var(--sh-bar);
  position: relative;
  z-index: 9;
}
.pubfoot p {
  margin-top: 9px;
  text-align: center;
  font-size: 11px;
  color: var(--text-2);
}

/* 图片操作层 */
.imgact {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.imgact button {
  flex: 1;
  height: 46px;
  border-radius: 13px;
  font-size: 14.5px;
  font-weight: 500;
}
.imgact .bg {
  background: var(--field);
  color: #3a3a3c;
}
.imgact .bg:disabled {
  opacity: 0.45;
}
.imgact .danger {
  color: var(--red);
}
.tip {
  margin-top: 11px;
  font-size: 11.5px;
  color: #a0a0a5;
}

/* ── 骨架 / 错误 ── */
.sk-block,
.sk-form {
  margin: 16px;
  border-radius: 14px;
  background: linear-gradient(100deg, #f4f4f6 30%, #ececf0 50%, #f4f4f6 70%);
  background-size: 220% 100%;
  animation: shimmer 1.25s linear infinite;
}
.sk-block {
  height: 110px;
}
.sk-form {
  height: 312px;
}
@keyframes shimmer {
  from { background-position: 140% 0; }
  to { background-position: -40% 0; }
}
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 80px 30px;
  color: var(--text-4);
}
.state .msg {
  font-size: var(--fs-2);
  color: var(--text-2);
  text-align: center;
}
.retry {
  margin-top: 4px;
  height: 34px;
  padding: 0 20px;
  border-radius: 10px;
  background: var(--field);
  color: var(--pink-dp);
  font-size: var(--fs-2);
}
</style>
