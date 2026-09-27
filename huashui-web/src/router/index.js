import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/request'
import PlaceholderView from '@/views/PlaceholderView.vue'

/* ==========================================================
   路由表 —— 与 docs/02-前端设计V1.md 的 14 个页面一一对应
   meta 字段说明：
     title  页面标题（顶栏用）
     tab    底栏高亮项（home | publish | mine），不参与则留空
     auth   是否需要登录
     block  计划在哪个块实现（块 1 只搭骨架，页面按块替换）
     apis   本页会调用的接口（作为「活清单」，实现时逐条划掉）
   ========================================================== */

const Placeholder = PlaceholderView

const routes = [
  {
    path: '/',
    name: 'home',
    // 块 3-A 已实现
    component: () => import('@/views/HomeView.vue'),
    meta: {
      title: '首页',
      tab: 'home',
      apis: ['GET /api/category/list', 'GET /api/product/list?sort=views', 'GET /api/product/list?sort=newest']
    }
  },
  {
    path: '/search',
    name: 'search',
    // 块 3-A 已实现
    component: () => import('@/views/SearchView.vue'),
    meta: {
      title: '搜索结果',
      apis: ['GET /api/product/list (kw / categoryId / campus / conditionLevel / sort)']
    }
  },
  {
    path: '/product/:id',
    name: 'productDetail',
    // 块 3-B 已实现
    component: () => import('@/views/ProductDetailView.vue'),
    meta: {
      title: '商品详情',
      apis: [
        'GET /api/product/detail/{id}',
        'GET /api/user/detail/{sellerId}',
        'GET /api/order/sold-count/{sellerId}',
        'POST|DELETE /api/favorite/{id}',
        'GET /api/user/{sellerId}/contact',
        'POST /api/order'
      ]
    }
  },
  {
    path: '/seller/:id',
    name: 'seller',
    // 块 3-A 已实现
    component: () => import('@/views/SellerView.vue'),
    meta: {
      title: '卖家主页',
      apis: ['GET /api/user/detail/{id}', 'GET /api/order/sold-count/{id}', 'GET /api/product/list?sellerId=']
    }
  },
  {
    path: '/publish',
    name: 'publish',
    // 块 4-A 已实现
    component: () => import('@/views/PublishView.vue'),
    meta: {
      title: '发布商品',
      // 🔴 刻意**不设 tab**：设计文档 §2.2 明确「发布」是不进底栏的二级页
      // （顶部自带「取消」，底部是自己的提交栏）。底栏的「发布」按钮仍作为入口跳到这里。
      auth: true,
      apis: ['GET /api/category/list', 'POST /api/file/upload', 'POST /api/product']
    }
  },
  {
    path: '/publish/:id',
    name: 'publishEdit',
    // 块 4-A 已实现：与发布页复用同一组件，靠 :id 区分编辑态
    component: () => import('@/views/PublishView.vue'),
    meta: {
      title: '编辑商品',
      auth: true,
      apis: ['GET /api/product/detail/{id}', 'PUT /api/product/{id}', 'POST /api/file/upload']
    }
  },
  {
    path: '/user/products',
    name: 'myProducts',
    // 块 4-B 已实现
    component: () => import('@/views/MyProductsView.vue'),
    meta: {
      title: '我的发布',
      auth: true,
      apis: [
        'GET /api/product/mine',
        'PUT /api/product/{id}/off-shelf',
        'PUT /api/product/{id}/on-shelf',
        'DELETE /api/product/{id}'
      ]
    }
  },
  {
    path: '/user/orders',
    name: 'myOrders',
    // 块 5-A 已实现
    component: () => import('@/views/MyOrdersView.vue'),
    meta: {
      title: '我的订单',
      auth: true,
      apis: [
        'GET /api/order/mine?role=buyer|seller',
        'GET /api/user/detail/{peerId}（补对端昵称，OrderVO 不含）',
        'PUT /api/order/{orderNo}/pay',
        'PUT /api/order/{orderNo}/complete',
        'PUT /api/order/{orderNo}/cancel'
      ]
    }
  },
  {
    path: '/order/:orderNo',
    name: 'orderDetail',
    // 块 5-B 已实现
    component: () => import('@/views/OrderDetailView.vue'),
    meta: {
      title: '订单详情',
      auth: true,
      apis: [
        'GET /api/order/detail/{orderNo}',
        'GET /api/user/detail/{peerId}',
        'GET /api/user/{peerId}/contact',
        'PUT /api/order/{orderNo}/pay',
        'PUT /api/order/{orderNo}/complete',
        'PUT /api/order/{orderNo}/cancel'
      ]
    }
  },
  {
    path: '/user/favorites',
    name: 'favorites',
    component: Placeholder,
    meta: { title: '我的收藏', auth: true, block: 6, apis: ['GET /api/favorite/mine', 'DELETE /api/favorite/{id}'] }
  },
  {
    path: '/notices',
    name: 'notices',
    component: Placeholder,
    meta: {
      title: '通知中心',
      auth: true,
      block: 6,
      apis: ['纯前端聚合：GET /api/product/mine + GET /api/order/mine（无专用接口）']
    }
  },
  {
    path: '/mine',
    name: 'mine',
    // 块 2 已实现
    component: () => import('@/views/MineView.vue'),
    meta: {
      title: '我的',
      tab: 'mine',
      auth: true,
      apis: ['GET /api/user/info', 'GET /api/product/mine', 'GET /api/order/mine?status=0', 'GET /api/favorite/mine?size=1']
    }
  },
  {
    path: '/user/profile',
    name: 'profile',
    // 块 2 已实现
    component: () => import('@/views/ProfileView.vue'),
    meta: {
      title: '个人资料',
      auth: true,
      apis: ['GET /api/user/info', 'PUT /api/user/info', 'PUT /api/user/password', 'POST /api/file/upload']
    }
  },
  {
    path: '/login',
    name: 'login',
    // 块 2 已实现
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', apis: ['POST /api/user/login'], guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    // 块 2 已实现
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册', apis: ['POST /api/user/register'], guestOnly: true }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: Placeholder,
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

/* 路由守卫：只做「有没有 token」这一层判定。
   真正的权限（是不是本人、是不是买卖双方）由后端判定，前端不重复实现。 */
router.beforeEach((to) => {
  const logged = !!getToken()

  if (to.meta.auth && !logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && logged) {
    return { path: '/' }
  }
  return true
})

export default router
