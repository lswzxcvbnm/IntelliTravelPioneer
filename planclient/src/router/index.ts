import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import Login from '../views/user/Login.vue'
import Register from '../views/user/Register.vue'

const BattleCockpit = () => import('../views/battle/BattleCockpit.vue')
const BattleCustomizePlan = () => import('../views/battle/BattleCustomizePlan.vue')
const ToolsManagement = () => import('../views/battle/ToolsManagement.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
  routes: [
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/login',
      name: 'login',
      component: Login
    },
    {
      path: '/register',
      name: 'register',
      component: Register
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/user/Dashboard.vue')
    },
    {
      path: '/battle',
      name: 'BattleCockpit',
      component: BattleCockpit
    },
    {
      path: '/battle/create',
      name: 'BattleCustomizePlan',
      component: BattleCustomizePlan
    },
    {
      path: '/tools',
      name: 'ToolsManagement',
      component: ToolsManagement
    },
    {
      path: '/conversation',
      name: 'ConversationList',
      component: () => import('../views/conversation/ConversationList.vue')
    },
    {
      path: '/conversation/:id',
      name: 'ConversationDetail',
      component: () => import('../views/conversation/ConversationDetail.vue')
    }
  ]
})

const routesRequireAuth = [
  '/battle',
  '/battle/create',
  '/tools',
  '/conversation'
]

router.beforeEach((to, _from, next) => {
  const requiresAuth = routesRequireAuth.some(route => to.path === route || to.path.startsWith(route + '/'))

  if (!requiresAuth) {
    return next()
  }

  const authStore = useAuthStore()
  const isLoggedIn = Boolean((authStore.token && authStore.token.length > 0) || authStore.user)

  if (!isLoggedIn) {
    import('element-plus').then(({ ElMessage }) => {
      ElMessage.warning('请先登录')
    })
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  next()
})

export default router
