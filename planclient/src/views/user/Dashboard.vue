<template>
  <div class="dashboard-mobile">
    <header class="home-header">
      <div class="header-left">
        <div class="greeting">
          <h1>
            <span class="greeting-hello">你好</span>
            <span class="greeting-name">，{{ isLoggedIn ? authStore.user?.username : '旅行者' }}</span>
          </h1>
          <p @click="!isLoggedIn && goLogin()" :style="!isLoggedIn ? 'cursor: pointer;' : ''">
            <span v-if="isLoggedIn">准备好开始新的旅程了吗？</span>
            <span v-else>当前未登录，点击立即<span style="color: #3385FF; font-weight: bold;">登录</span></span>
          </p>
        </div>
      </div>
    </header>

    <section class="action-buttons">
      <div class="btn-action" @click="handleProtectedNavigation('/battle/create')">
        <div class="btn-action-bg"></div>
        <div class="btn-action-content">
          <span class="btn-action-icon">🚀</span>
          <span class="btn-action-title">新建行程规划</span>
        </div>
      </div>
      <div class="btn-action btn-secondary" @click="handleProtectedNavigation('/battle')">
        <div class="btn-action-content">
          <span class="btn-action-icon">⚔️</span>
          <span class="btn-action-title">行程规划仓库</span>
        </div>
      </div>
    </section>

    <section class="destinations-section">
      <div class="section-header">
        <span class="section-title">热门目的地</span>
      </div>

      <div class="dest-grid">
        <div
          class="dest-item dest-hero"
          @click="handleProtectedNavigation({ path: '/battle/create', query: { destination: currentDestinations[0]?.name } })"
        >
          <transition name="fade" mode="out-in">
            <div :key="currentDestinations[0]?.name" class="dest-image-wrapper">
              <img v-if="currentDestinations[0]?.img" :src="currentDestinations[0].img" :alt="currentDestinations[0].name" referrerpolicy="no-referrer" />
              <div v-else class="dest-placeholder"></div>
            </div>
          </transition>
          <div class="dest-info">
            <span class="dest-name">{{ currentDestinations[0]?.name }}</span>
          </div>
        </div>

        <div class="dest-right-group">
          <div
            class="dest-item dest-top"
            @click="handleProtectedNavigation({ path: '/battle/create', query: { destination: currentDestinations[1]?.name } })"
          >
            <transition name="fade" mode="out-in">
              <div :key="currentDestinations[1]?.name" class="dest-image-wrapper">
                <img v-if="currentDestinations[1]?.img" :src="currentDestinations[1].img" :alt="currentDestinations[1].name" referrerpolicy="no-referrer" />
                <div v-else class="dest-placeholder"></div>
              </div>
            </transition>
            <div class="dest-info">
              <span class="dest-name">{{ currentDestinations[1]?.name }}</span>
            </div>
          </div>

          <div class="dest-bottom-row">
            <div
              class="dest-item dest-small"
              @click="handleProtectedNavigation({ path: '/battle/create', query: { destination: currentDestinations[2]?.name } })"
            >
              <transition name="fade" mode="out-in">
                <div :key="currentDestinations[2]?.name" class="dest-image-wrapper">
                  <img v-if="currentDestinations[2]?.img" :src="currentDestinations[2].img" :alt="currentDestinations[2].name" referrerpolicy="no-referrer" />
                  <div v-else class="dest-placeholder"></div>
                </div>
              </transition>
              <div class="dest-info">
                <span class="dest-name">{{ currentDestinations[2]?.name }}</span>
              </div>
            </div>

            <div
              class="dest-item dest-small"
              @click="handleProtectedNavigation({ path: '/battle/create', query: { destination: currentDestinations[3]?.name } })"
            >
              <transition name="fade" mode="out-in">
                <div :key="currentDestinations[3]?.name" class="dest-image-wrapper">
                  <img v-if="currentDestinations[3]?.img" :src="currentDestinations[3].img" :alt="currentDestinations[3].name" referrerpolicy="no-referrer" />
                  <div v-else class="dest-placeholder"></div>
                </div>
              </transition>
              <div class="dest-info">
                <span class="dest-name">{{ currentDestinations[3]?.name }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">

import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const isLoggedIn = computed(() => Boolean(authStore.token && authStore.user))

const allCities = [
  '北京', '上海', '天津', '重庆', '哈尔滨', '长春', '沈阳', '呼和浩特', '石家庄',
  '乌鲁木齐', '兰州', '西宁', '西安', '银川', '郑州', '济南', '太原', '合肥',
  '武汉', '长沙', '南京', '成都', '贵阳', '昆明', '南宁', '拉萨', '杭州',
  '南昌', '广州', '福州', '海口', '深圳', '厦门', '三亚', '大连', '青岛',
  '桂林', '丽江', '大理', '苏州', '黄山', '张家界', '洛阳'
]

const displayDestinations = ref<{ name: string; img: string }[]>([])
const currentDestinations = ref<{ name: string; img: string }[]>([])
const rotationIndex = ref(0)
let rotationTimer: ReturnType<typeof setInterval> | null = null

const checkCityImage = (name: string): Promise<string> => {
  return new Promise((resolve) => {
    const exts = ['.jpg', '.png', '.jpeg', '.webp']
    let i = 0
    const tryNext = () => {
      if (i >= exts.length) {
        resolve('')
        return
      }
      const url = `/city/${name}${exts[i]}`
      const img = new Image()
      img.onload = () => resolve(url)
      img.onerror = () => {
        i++
        tryNext()
      }
      img.src = url
    }
    tryNext()
  })
}

const loadDestinations = async () => {
  const shuffled = [...allCities].sort(() => 0.5 - Math.random())
  const names = shuffled.slice(0, 8)

  const selected = await Promise.all(names.map(async (name) => ({
    name,
    img: await checkCityImage(name)
  })))

  displayDestinations.value = selected
  currentDestinations.value = selected.slice(0, 4)
}

const rotateDestinations = () => {
  if (displayDestinations.value.length < 8) return

  rotationIndex.value = (rotationIndex.value + 1) % (displayDestinations.value.length - 3)
  currentDestinations.value = displayDestinations.value.slice(rotationIndex.value, rotationIndex.value + 4)
}

onMounted(() => {
  setTimeout(() => {
    loadDestinations().catch(e => console.warn('加载目的地图片执行异常', e))
  }, 100)

  rotationTimer = setInterval(() => {
    rotateDestinations()
  }, 5000)
})

onUnmounted(() => {
  if (rotationTimer) {
    clearInterval(rotationTimer)
  }
})

const goLogin = () => {
  router.push('/login')
}

const handleProtectedNavigation = async (target: string | Record<string, any>) => {
  if (!isLoggedIn.value) {
    ElMessage.info('请先登录')
    goLogin()
    return
  }

  router.push(target as any)
}
</script>

<style scoped>
.dashboard-mobile {
  padding: 16px 16px calc(80px + env(safe-area-inset-bottom, 0px));
  background: linear-gradient(180deg, #FFFFFF 0%, #F7FBFF 50%, #F4F9FF 100%);
  min-height: 100dvh;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 20px 22px;
  background: linear-gradient(135deg, #FFFFFF 0%, #F8FBFF 100%);
  border-radius: 24px;
  border: 1px solid rgba(92, 168, 255, 0.08);
  box-shadow: 0 2px 12px rgba(92, 168, 255, 0.06);
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.greeting {
  min-width: 0;
}

.greeting h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.greeting-hello {
  color: #2563EB;
}

.greeting-name {
  color: #04107d;
}

.greeting p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.4;
  color: #4A6B99;
}

.action-buttons {
  display: flex;
  gap: 14px;
  margin-bottom: 16px;
}

.btn-action {
  flex: 1;
  position: relative;
  border-radius: 24px;
  overflow: hidden;
  cursor: pointer;
  min-height: 130px;
  background: transparent;
}

.btn-action-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #8AC4FF 0%, #5CA8FF 50%, #3385FF 100%);
  transition: transform 0.3s ease;
}

.btn-action:hover .btn-action-bg {
  transform: scale(1.03);
}

.btn-action:active {
  transform: scale(0.98);
}

.btn-action-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px 16px;
  height: 130px;
}

.btn-action-icon {
  font-size: 48px;
}

.btn-action-title {
  font-size: 16px;
  font-weight: 700;
  color: white;
  text-align: center;
  line-height: 1.3;
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.95);
  border: 1.5px solid rgba(92, 168, 255, 0.15);
  box-shadow: 0 2px 12px rgba(92, 168, 255, 0.06);
}

.btn-secondary .btn-action-title {
  color: #2D4A6F;
}

.destinations-section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding: 0 2px;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #2D4A6F;
}

.dest-grid {
  display: flex;
  gap: 12px;
  height: 240px;
}

.dest-item {
  position: relative;
  border-radius: 18px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 3px 12px rgba(92, 168, 255, 0.08);
}

.dest-item::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 40%, rgba(0,0,0,0.5) 100%);
  z-index: 1;
}

.dest-item:hover {
  transform: translateY(-3px) scale(1.02);
  box-shadow: 0 8px 24px rgba(92, 168, 255, 0.18);
}

.dest-item:active {
  transform: scale(0.97);
}

.dest-hero {
  flex: 1.2;
  height: 100%;
}

.dest-right-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dest-top {
  flex: 1;
}

.dest-bottom-row {
  flex: 1;
  display: flex;
  gap: 12px;
}

.dest-small {
  flex: 1;
}

.dest-image-wrapper {
  width: 100%;
  height: 100%;
}

.dest-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.dest-item:hover img {
  transform: scale(1.08);
}

.dest-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #EBF5FF 0%, #D6EAFF 50%, #C2DAFF 100%);
  background-size: 200% 200%;
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.dest-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 14px 16px;
  z-index: 2;
}

.dest-small .dest-info {
  padding: 8px 10px;
}

.dest-name {
  color: white;
  font-weight: 700;
  font-size: 17px;
  text-shadow: 0 1px 4px rgba(0,0,0,0.3);
}

.dest-small .dest-name {
  font-size: 13px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
