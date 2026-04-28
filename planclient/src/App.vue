<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const showBottomNav = computed(() => {
  const mainTabs = ['/dashboard', '/battle']
  if (route.path === '/battle/create') return false
  return mainTabs.includes(route.path)
})
</script>

<template>
  <div class="app-container" :class="{ 'has-nav': showBottomNav }">
    <router-view></router-view>
  </div>
  <nav v-if="showBottomNav" class="bottom-nav">
    <router-link to="/dashboard" class="nav-item" :class="{ active: route.path === '/dashboard' }">
      <span class="nav-icon">🏠</span>
      <span>首页</span>
    </router-link>
    <router-link to="/battle" class="nav-item" :class="{ active: route.path === '/battle' }">
      <span class="nav-icon">📋</span>
      <span>行程</span>
    </router-link>
  </nav>
</template>

<style scoped>
.app-container.has-nav {
  padding-bottom: 70px;
}
.app-container.has-nav > * {
  min-height: calc(100dvh - 70px);
  box-sizing: border-box;
}

.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  background: #fff;
  border-top: 1px solid rgba(0,0,0,0.06);
  box-shadow: 0 -4px 16px rgba(51, 133, 255, 0.06);
  z-index: 100;
  padding: 6px 0 env(safe-area-inset-bottom, 0);
}

.nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 0;
  text-decoration: none;
  color: #7A9AC4;
  font-size: 11px;
  font-weight: 500;
  transition: color 0.2s;
}

.nav-item.active {
  color: #3385FF;
}

.nav-icon {
  font-size: 22px;
}
</style>
