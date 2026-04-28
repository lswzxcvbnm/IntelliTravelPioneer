import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'

const EXIT_THRESHOLD = 2000
const MAIN_TABS = ['/dashboard', '/battle']
let isHandlingBack = false
let lastBackTime = 0

export const getParentMainTab = (path: string): string | null => {
  if (path.startsWith('/battle/')) return '/battle'
  return null
}

export const goBackByAppRule = (router: Router, event?: PopStateEvent) => {
  if (isHandlingBack) return
  isHandlingBack = true

  try {
    const currentPath = router.currentRoute.value.path
    const parentTab = getParentMainTab(currentPath)

    if (MAIN_TABS.includes(currentPath) && currentPath !== '/dashboard') {
      router.push('/dashboard')
      event?.preventDefault()
      return
    }

    if (parentTab && parentTab !== currentPath) {
      router.push(parentTab)
      event?.preventDefault()
      return
    }

    if (!MAIN_TABS.includes(currentPath) && window.history.state?.back) {
      router.back()
      event?.preventDefault()
      return
    }

    const now = Date.now()
    if (now - lastBackTime < EXIT_THRESHOLD) {
      window.history.back()
      return
    }

    lastBackTime = now
    ElMessage({
      message: '再按一次退出',
      type: 'info',
      duration: EXIT_THRESHOLD,
      offset: window.innerHeight > 768 ? 20 : 60
    })
    event?.preventDefault()
  } finally {
    setTimeout(() => {
      isHandlingBack = false
    }, 100)
  }
}

export function useBackHandler() {
  const router = useRouter()

  const handlePopState = (event?: PopStateEvent) => {
    goBackByAppRule(router, event)
  }

  onMounted(() => {
    window.addEventListener('popstate', handlePopState)
  })

  onUnmounted(() => {
    window.removeEventListener('popstate', handlePopState)
  })
}
