import { createRouter, createWebHistory } from 'vue-router'
import { TOKEN_KEY } from '@/utils/request'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginPage.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layout/AdminLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardPage.vue'),
          meta: { title: '今日数据' },
        },
        {
          path: 'employee',
          name: 'employee',
          component: () => import('@/views/employee/EmployeePage.vue'),
          meta: { title: '员工管理' },
        },
        {
          path: 'category',
          name: 'category',
          component: () => import('@/views/category/CategoryPage.vue'),
          meta: { title: '分类管理' },
        },
        {
          path: 'drink',
          name: 'drink',
          component: () => import('@/views/drink/DrinkPage.vue'),
          meta: { title: '饮品管理' },
        },
        {
          path: 'drink/edit/:id?',
          name: 'drink-edit',
          component: () => import('@/views/drink/DrinkEditPage.vue'),
          meta: { title: '饮品编辑' },
        },
        {
          path: 'setmeal',
          name: 'setmeal',
          component: () => import('@/views/setmeal/SetmealPage.vue'),
          meta: { title: '套餐管理' },
        },
        {
          path: 'order',
          name: 'order',
          component: () => import('@/views/order/OrderPage.vue'),
          meta: { title: '订单管理' },
        },
        {
          path: 'report',
          name: 'report',
          component: () => import('@/views/report/ReportPage.vue'),
          meta: { title: '数据统计' },
        },
        {
          path: 'coupon',
          name: 'coupon',
          component: () => import('@/views/coupon/CouponPage.vue'),
          meta: { title: '优惠券管理' },
        },
        {
          path: 'shop',
          name: 'shop',
          component: () => import('@/views/shop/ShopPage.vue'),
          meta: { title: '店铺设置' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

/* 登录守卫：无 token 一律回登录页 */
router.beforeEach((to) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (to.path === '/login') {
    return token ? '/' : true
  }
  if (!token) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · 山月茶咖` : '山月茶咖 · 管理后台'
})

export default router
