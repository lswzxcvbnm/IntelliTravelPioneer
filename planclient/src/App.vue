<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLoggedIn = computed(() => Boolean(authStore.token && authStore.user))

const isAuthPage = computed(() =>
  route.path === '/login' || route.path === '/register'
)

const isBattleCreate = computed(() => route.path === '/battle/create')

const navItems = [
  { path: '/dashboard', label: '首页', icon: '🏠' },
  { path: '/battle', label: '行程仓库', icon: '📋' },
  { path: '/battle/skills', label: '技能工作台', icon: '🧩' },
  { path: '/tools', label: '工具管理', icon: '🔧' },
  { path: '/conversation', label: 'AI 对话', icon: '💬' },
]

const goCreate = () => {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }
  router.push('/battle/create')
}

const handleLogout = () => {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside v-if="!isAuthPage" class="sidebar">
      <div class="sidebar-brand" @click="router.push('/dashboard')">
        <span class="brand-icon">✈️</span>
        <span class="brand-text">IntelliTravel</span>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ active: route.path === item.path }"
        >
          <span class="nav-link-icon">{{ item.icon }}</span>
          <span class="nav-link-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-spacer"></div>

      <button class="btn-create" @click="goCreate">
        <span>＋</span>
        <span>新建行程</span>
      </button>

      <div class="sidebar-user" v-if="isLoggedIn">
        <div class="user-avatar">{{ authStore.user?.username?.charAt(0)?.toUpperCase() || 'U' }}</div>
        <div class="user-info">
          <span class="user-name">{{ authStore.user?.username }}</span>
        </div>
        <button class="btn-logout" @click="handleLogout" title="退出登录">⏻</button>
      </div>
      <div class="sidebar-user" v-else>
        <button class="btn-login-prompt" @click="router.push('/login')">登录 / 注册</button>
      </div>
    </aside>

    <main class="main-content" :class="{ 'full-width': isAuthPage, 'no-sidebar': isAuthPage }">
      <router-view></router-view>
    </main>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  background: #F5F8FC;
  color: #1E3A5F;
  -webkit-font-smoothing: antialiased;
}

#app {
  height: 100%;
}
</style>

<style scoped>
.app-shell {
  display: flex;
  height: 100dvh;
  overflow: hidden;
}

.sidebar {
  width: 240px;
  min-width: 240px;
  height: 100dvh;
  background: linear-gradient(180deg, #ffffff 0%, #F8FBFF 100%);
  border-right: 1px solid rgba(51, 133, 255, 0.1);
  display: flex;
  flex-direction: column;
  padding: 20px 16px;
  z-index: 50;
  box-shadow: 1px 0 12px rgba(51, 133, 255, 0.04);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 20px;
  cursor: pointer;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
  margin-bottom: 16px;
}

.brand-icon {
  font-size: 28px;
}

.brand-text {
  font-size: 18px;
  font-weight: 800;
  color: #1A6FFF;
  letter-spacing: -0.5px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  text-decoration: none;
  color: #5B7FA5;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.15s ease;
}

.nav-link:hover {
  background: rgba(51, 133, 255, 0.06);
  color: #3385FF;
}

.nav-link.active {
  background: rgba(51, 133, 255, 0.1);
  color: #1A6FFF;
  font-weight: 600;
}

.nav-link-icon {
  font-size: 20px;
  width: 24px;
  text-align: center;
}

.sidebar-spacer {
  flex: 1;
}

.btn-create {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 13px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 14px rgba(51, 133, 255, 0.3);
  margin-bottom: 12px;
}

.btn-create:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(51, 133, 255, 0.4);
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 12px;
  border-radius: 12px;
  background: rgba(51, 133, 255, 0.04);
  border: 1px solid rgba(51, 133, 255, 0.08);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #3385FF, #1A6FFF);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 15px;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #1E3A5F;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-logout {
  background: none;
  border: none;
  color: #94A3B8;
  font-size: 16px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.15s;
}

.btn-logout:hover {
  background: rgba(229, 62, 62, 0.08);
  color: #E53E3E;
}

.btn-login-prompt {
  width: 100%;
  padding: 10px;
  border: 1.5px solid rgba(51, 133, 255, 0.2);
  border-radius: 10px;
  background: transparent;
  color: #3385FF;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-login-prompt:hover {
  background: rgba(51, 133, 255, 0.06);
}

.main-content {
  flex: 1;
  min-width: 0;
  height: 100dvh;
  overflow-y: auto;
  overflow-x: hidden;
  background: linear-gradient(180deg, #FFFFFF 0%, #F7FBFF 50%, #F4F9FF 100%);
}

.main-content.full-width {
  background: linear-gradient(180deg, #FFFFFF 0%, #F7FBFF 50%, #F4F9FF 100%);
}

.main-content.no-sidebar {
  width: 100%;
}
</style>
