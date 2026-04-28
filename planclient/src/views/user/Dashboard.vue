<template>
  <div class="dashboard-page">
    <div class="dashboard-grid">
      <section class="welcome-section">
        <div class="welcome-card">
          <div class="welcome-text">
            <h1>
              <span class="greeting-hello">你好</span>
              <span class="greeting-name">，{{ isLoggedIn ? authStore.user?.username : '旅行者' }}</span>
            </h1>
            <p @click="!isLoggedIn && goLogin()" :style="!isLoggedIn ? 'cursor: pointer;' : ''">
              <span v-if="isLoggedIn">准备好开始新的旅程了吗？</span>
              <span v-else>当前未登录，点击<span style="color:#3385FF;font-weight:bold;">登录</span>开启旅程</span>
            </p>
          </div>
          <div class="welcome-actions">
            <button class="action-btn-primary" @click="handleProtectedNavigation('/battle/create')">
              <span class="action-icon">🚀</span>
              <span>新建行程规划</span>
            </button>
            <button class="action-btn-secondary" @click="handleProtectedNavigation('/battle')">
              <span class="action-icon">📋</span>
              <span>行程仓库</span>
            </button>
          </div>
        </div>
      </section>

      <section class="destinations-section">
        <div class="section-header">
          <h2>热门目的地</h2>
          <span class="section-hint">点击城市卡片，直接为该城市创建行程</span>
        </div>

        <div class="dest-grid">
          <div
            v-for="(city, idx) in currentDestinations"
            :key="city.name"
            class="dest-card"
            :class="`dest-card-${idx}`"
            @click="handleProtectedNavigation({ path: '/battle/create', query: { destination: city.name } })"
          >
            <transition name="fade" mode="out-in">
              <div :key="city.name" class="dest-image-wrapper">
                <img v-if="city.img" :src="city.img" :alt="city.name" referrerpolicy="no-referrer" />
                <div v-else class="dest-placeholder"></div>
              </div>
            </transition>
            <div class="dest-overlay">
              <span class="dest-name">{{ city.name }}</span>
              <span class="dest-action">立即规划 →</span>
            </div>
          </div>
        </div>
      </section>
    </div>
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
      if (i >= exts.length) { resolve(''); return }
      const url = `/city/${name}${exts[i]}`
      const img = new Image()
      img.onload = () => resolve(url)
      img.onerror = () => { i++; tryNext() }
      img.src = url
    }
    tryNext()
  })
}

const loadDestinations = async () => {
  const shuffled = [...allCities].sort(() => 0.5 - Math.random())
  const names = shuffled.slice(0, 12)
  const selected = await Promise.all(names.map(async (name) => ({
    name,
    img: await checkCityImage(name)
  })))
  displayDestinations.value = selected
  currentDestinations.value = selected.slice(0, 6)
}

const rotateDestinations = () => {
  if (displayDestinations.value.length < 12) return
  rotationIndex.value = (rotationIndex.value + 1) % 6
  currentDestinations.value = displayDestinations.value.slice(rotationIndex.value, rotationIndex.value + 6)
}

onMounted(() => {
  setTimeout(() => { loadDestinations().catch(e => console.warn(e)) }, 100)
  rotationTimer = setInterval(() => { rotateDestinations() }, 6000)
})

onUnmounted(() => {
  if (rotationTimer) { clearInterval(rotationTimer) }
})

const goLogin = () => { router.push('/login') }

const handleProtectedNavigation = async (target: string | Record<string, any>) => {
  if (!isLoggedIn.value) { ElMessage.info('请先登录'); goLogin(); return }
  router.push(target as any)
}
</script>

<style scoped>
.dashboard-page {
  padding: 32px 40px;
  max-width: 1200px;
  margin: 0 auto;
  height: 100dvh;
  overflow-y: auto;
}

.dashboard-grid {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32px 36px;
  background: linear-gradient(135deg, #ffffff 0%, #F8FBFF 100%);
  border-radius: 20px;
  border: 1px solid rgba(51, 133, 255, 0.1);
  box-shadow: 0 2px 16px rgba(51, 133, 255, 0.05);
}

.welcome-text h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.greeting-hello { color: #2563EB; }
.greeting-name { color: #04107D; }

.welcome-text p {
  font-size: 15px;
  color: #5B7FA5;
  margin: 0;
}

.welcome-actions {
  display: flex;
  gap: 12px;
}

.action-btn-primary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 24px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 4px 14px rgba(51, 133, 255, 0.3);
}

.action-btn-primary:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(51, 133, 255, 0.4); }

.action-btn-secondary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 24px;
  border: 1.5px solid rgba(51, 133, 255, 0.15);
  border-radius: 14px;
  background: #fff;
  color: #3385FF;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn-secondary:hover { background: rgba(51, 133, 255, 0.05); }

.action-icon { font-size: 20px; }

.section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1E3A5F;
}

.section-hint {
  font-size: 13px;
  color: #94A3B8;
}

.dest-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  grid-template-rows: 200px 200px;
  gap: 14px;
}

.dest-card {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 3px 12px rgba(51, 133, 255, 0.06);
}

.dest-card:nth-child(1) { grid-row: span 2; }

.dest-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 30%, rgba(0,0,0,0.55) 100%);
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px 20px;
  transition: all 0.3s;
}

.dest-card:hover { transform: translateY(-4px); box-shadow: 0 8px 28px rgba(51, 133, 255, 0.15); }
.dest-card:hover img { transform: scale(1.06); }
.dest-card:hover .dest-action { opacity: 1; transform: translateY(0); }

.dest-image-wrapper { width: 100%; height: 100%; }

.dest-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.dest-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #EBF5FF 0%, #D6EAFF 50%, #C2DAFF 100%);
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% { opacity: 0.6; }
  50% { opacity: 1; }
  100% { opacity: 0.6; }
}

.dest-name {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 1px 4px rgba(0,0,0,0.3);
  margin-bottom: 4px;
}

.dest-card:nth-child(1) .dest-name { font-size: 24px; }

.dest-action {
  font-size: 13px;
  color: rgba(255,255,255,0.8);
  opacity: 0;
  transform: translateY(6px);
  transition: all 0.3s;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.6s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
