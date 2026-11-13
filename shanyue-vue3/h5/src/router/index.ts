import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '山月茶咖 · 扫码点单' },
    },
    {
      path: '/confirm',
      name: 'confirm',
      component: () => import('@/views/ConfirmView.vue'),
      meta: { title: '确认下单' },
    },
    {
      path: '/success/:pickUpCode',
      name: 'success',
      component: () => import('@/views/SuccessView.vue'),
      meta: { title: '下单成功' },
    },
    {
      path: '/query',
      name: 'query',
      component: () => import('@/views/QueryView.vue'),
      meta: { title: '订单查询' },
    },
    {
      path: '/member',
      name: 'member',
      component: () => import('@/views/MemberView.vue'),
      meta: { title: '会员中心' },
    },
    {
      path: '/shop-info',
      name: 'shop-info',
      component: () => import('@/views/ShopInfoView.vue'),
      meta: { title: '门店信息' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  if (title) document.title = title
})

export default router
