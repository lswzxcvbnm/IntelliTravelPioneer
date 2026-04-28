<template>
  <div class="cockpit-page">
    <header class="hero">
      <div class="hero-icon">
        <el-icon :size="32"><Compass /></el-icon>
      </div>
      <div class="hero-text">
        <h1>行程作战中心</h1>
        <p class="subtitle">统一管理你的所有 AI 行程方案，快速进入生成、编辑与执行。</p>
      </div>
    </header>

    <section class="stats-block" aria-label="统计概览">
      <div class="stats-block-title">
        <el-icon><DataAnalysis /></el-icon>
        <span>统计概览</span>
      </div>
      <div class="stats-block-grid">
        <div class="stat-item" role="figure" aria-label="行程总数">
          <div class="stat-label-row">
            <el-icon class="stat-icon" :size="16"><MapLocation /></el-icon>
            <span>行程总数</span>
          </div>
          <strong>{{ itineraries.length }}</strong>
        </div>
        <div class="stat-item" role="figure" aria-label="累计天数">
          <div class="stat-label-row">
            <el-icon class="stat-icon" :size="16"><Calendar /></el-icon>
            <span>累计天数</span>
          </div>
          <strong>{{ totalDays }}</strong>
        </div>
        <div class="stat-item" role="figure" aria-label="预计总预算">
          <div class="stat-label-row">
            <el-icon class="stat-icon" :size="16"><Wallet /></el-icon>
            <span>预计总预算</span>
          </div>
          <strong>{{ formatCurrency(totalBudget) }}</strong>
        </div>
      </div>
    </section>

    <section class="quick-actions" aria-label="快速操作">
      <button class="primary-btn" type="button" @click="goCreate" aria-label="新建作战计划">
        <el-icon><Plus /></el-icon>
        <span>新建作战计划</span>
      </button>
    </section>

    <section class="panel" aria-label="我的计划库">
      <div class="panel-title-row">
        <h2>
          <el-icon><FolderOpened /></el-icon>
          <span>我的计划库</span>
        </h2>
        <span class="meta">按更新时间排序</span>
      </div>

      <div v-if="errorMessage" class="empty-state error" role="alert">
        <el-icon :size="48"><WarningFilled /></el-icon>
        <p>{{ errorMessage }}</p>
        <button class="retry-btn" type="button" @click="refreshList">
          <el-icon><RefreshRight /></el-icon>
          <span>重试</span>
        </button>
      </div>

      <div v-else-if="isLoading" class="skeleton-container" aria-busy="true" aria-label="加载中">
        <div v-for="i in 3" :key="i" class="skeleton-card">
          <div class="skeleton-main">
            <div class="skeleton-title"></div>
            <div class="skeleton-desc"></div>
            <div class="skeleton-meta"></div>
          </div>
          <div class="skeleton-actions">
            <div class="skeleton-btn"></div>
            <div class="skeleton-btn"></div>
          </div>
        </div>
      </div>

      <div v-else-if="itineraries.length === 0" class="empty-state" role="status">
        <el-icon :size="64" class="empty-icon"><Document /></el-icon>
        <p class="empty-title">还没有作战计划</p>
        <p class="empty-desc">点击下方按钮开始创建你的第一个行程计划</p>
        <button class="primary-btn empty-action" type="button" @click="goCreate">
          <el-icon><Plus /></el-icon>
          <span>创建第一个计划</span>
        </button>
      </div>

      <TransitionGroup v-else name="plan-list" tag="ul" class="plan-list">
        <li v-for="item in itineraries" :key="item.id" class="plan-card" tabindex="0">
          <div class="plan-main">
            <div class="plan-header">
              <p class="plan-title">{{ item.title }}</p>
              <span class="plan-badge" :class="getPlanStatus(item).class">
                {{ getPlanStatus(item).text }}
              </span>
            </div>
            <p class="plan-desc">{{ getPlanSummary(item.plan) }}</p>
            <div class="plan-meta-row">
              <span class="plan-meta-item">
                <el-icon :size="14"><Calendar /></el-icon>
                {{ item.plan.totalDays }} 天
              </span>
              <span class="plan-meta-item">
                <el-icon :size="14"><Wallet /></el-icon>
                {{ formatCurrency(item.plan.estimatedCost) }}
              </span>
              <span class="plan-meta-item">
                <el-icon :size="14"><Clock /></el-icon>
                {{ formatDate(item.updateTime) }}
              </span>
            </div>
          </div>
          <div class="plan-actions">
            <button type="button" class="action-btn edit-btn" @click="goEdit(item.id)" aria-label="编辑计划">
              <el-icon><Edit /></el-icon>
              <span>编辑</span>
            </button>
            <button
              type="button"
              class="action-btn delete-btn"
              @click="openDeleteModal(item)"
              :disabled="isDeleting"
              aria-label="删除计划"
            >
              <el-icon><Delete /></el-icon>
              <span>删除</span>
            </button>
          </div>
        </li>
      </TransitionGroup>
    </section>

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showDeleteModal" class="modal-overlay" @click.self="closeDeleteModal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
          <div class="modal-content">
            <div class="modal-header">
              <el-icon class="modal-icon" :size="24"><WarningFilled /></el-icon>
              <h3 id="modal-title">确认删除</h3>
            </div>
            <p class="modal-message">
              确定要删除行程计划 <strong>{{ deletingItem?.title }}</strong> 吗？<br />
              <span class="modal-warning">此操作不可恢复。</span>
            </p>
            <div class="modal-actions">
              <button type="button" class="modal-btn cancel-btn" @click="closeDeleteModal">
                取消
              </button>
              <button type="button" class="modal-btn confirm-btn" @click="confirmDelete" :disabled="isDeleting">
                <span v-if="isDeleting" class="loading-spinner"></span>
                <span>{{ isDeleting ? '删除中...' : '确认删除' }}</span>
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Compass,
  DataAnalysis,
  MapLocation,
  Calendar,
  Wallet,
  Plus,
  FolderOpened,
  WarningFilled,
  RefreshRight,
  Document,
  Clock,
  Edit,
  Delete
} from '@element-plus/icons-vue'
import { getAllItineraries, getItineraryDetail, deleteItinerary } from '../../api/itinerary'
import { useAuthStore } from '../../stores/auth'
import type { ItineraryVO, ItineraryPlan } from '../../api/itinerary'

const router = useRouter()
const authStore = useAuthStore()

const isLoading = ref(false)
const errorMessage = ref('')
const itineraries = ref<ItineraryVO[]>([])

const showDeleteModal = ref(false)
const deletingItem = ref<ItineraryVO | null>(null)
const isDeleting = ref(false)

const totalDays = computed(() =>
  itineraries.value.reduce((sum, itinerary) => sum + itinerary.plan.totalDays, 0)
)

const totalBudget = computed(() =>
  itineraries.value.reduce((sum, itinerary) => sum + itinerary.plan.estimatedCost, 0)
)

const formatCurrency = (value: number) => `￥${value.toLocaleString('zh-CN')}`

const formatDate = (value: string) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return `${diffDays}天前`
  return date.toLocaleDateString('zh-CN')
}

const getPlanSummary = (plan: ItineraryPlan) => {
  const firstItem = plan.dailyPlans[0]?.items[0]
  if (!firstItem) {
    return '暂无路线项，请前往编辑页完善行程。'
  }
  return `首日推荐：${firstItem.name}（${firstItem.type}）`
}

const getPlanStatus = (item: ItineraryVO) => {
  const updateDate = new Date(item.updateTime)
  const now = new Date()
  const diffDays = Math.floor((now.getTime() - updateDate.getTime()) / (1000 * 60 * 60 * 24))

  if (diffDays === 0) return { text: '今日更新', class: 'status-new' }
  if (diffDays <= 3) return { text: '近期更新', class: 'status-recent' }
  return { text: '较早', class: 'status-old' }
}

const refreshList = async () => {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const idRes = await getAllItineraries()
    const ids = idRes.data.data || []

    if (ids.length === 0) {
      itineraries.value = []
      return
    }

    const detailRequests = ids.map((id) => getItineraryDetail(id))
    const detailResults = await Promise.all(detailRequests)
    const detailList = detailResults.map((result) => result.data.data)

    itineraries.value = detailList.sort((a, b) => {
      const left = new Date(a.updateTime).getTime()
      const right = new Date(b.updateTime).getTime()
      return right - left
    })
  } catch (error) {
    console.error('加载行程列表失败:', error)
    errorMessage.value = '行程加载失败，请稍后重试。'
  } finally {
    isLoading.value = false
  }
}

const goCreate = () => {
  router.push('/battle/create')
}

const goEdit = (itineraryId: string) => {
  router.push({
    path: '/battle/create',
    query: { itineraryId }
  })
}

const openDeleteModal = (item: ItineraryVO) => {
  deletingItem.value = item
  showDeleteModal.value = true
}

const closeDeleteModal = () => {
  if (isDeleting.value) return
  showDeleteModal.value = false
  deletingItem.value = null
}

const confirmDelete = async () => {
  if (!deletingItem.value) return

  isDeleting.value = true
  try {
    await deleteItinerary(deletingItem.value.id)
    itineraries.value = itineraries.value.filter((i) => i.id !== deletingItem.value!.id)
    ElMessage.success('行程计划已删除')
  } catch (err) {
    console.error('删除行程失败:', err)
    ElMessage.error('删除失败，请稍后重试')
  } finally {
    isDeleting.value = false
    showDeleteModal.value = false
    deletingItem.value = null
  }
}

onMounted(() => {
  refreshList()
})
</script>

<style scoped>
.cockpit-page {
  min-height: 100dvh;
  padding: 18px 16px calc(92px + env(safe-area-inset-bottom, 0px));
  background: linear-gradient(180deg, #FFFFFF 0%, #EBF5FF 50%, #F5FAFF 100%);
  color: #1E3A5F;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.hero {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border-radius: 20px;
  background: linear-gradient(135deg, #1A6FFF 0%, #3385FF 50%, #0057E5 100%);
  color: #ffffff;
  box-shadow: 0 10px 28px rgba(51, 133, 255, 0.3);
}

.hero-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  color: #ffffff;
}

.hero-text {
  flex: 1;
}

.hero h1 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
}

.subtitle {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  opacity: 0.9;
  color: #ffffff;
}

.stats-block {
  margin-top: 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid rgba(51, 133, 255, 0.12);
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(51, 133, 255, 0.08);
}

.stats-block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  font-size: 14px;
  color: #3B5998;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
  background: linear-gradient(180deg, #F8FBFF 0%, #F0F7FF 100%);
  font-weight: 600;
}

.stats-block-grid {
  display: flex;
  align-items: stretch;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  border-right: 1px solid rgba(51, 133, 255, 0.08);
  transition: background 0.2s ease;
  flex: 1;
  min-width: 0;
}

.stat-item:last-child {
  border-right: none;
}

.stat-item:hover {
  background: rgba(51, 133, 255, 0.03);
}

.stat-icon {
  color: #3385FF;
  flex-shrink: 0;
}

.stat-label-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6B8CBE;
}

.stat-item strong {
  display: block;
  margin-top: 4px;
  font-size: 16px;
  font-weight: 700;
  color: #1E3A5F;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.primary-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 14px;
  padding: 14px 16px;
  border: none;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.25s ease;
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  box-shadow: 0 4px 14px rgba(51, 133, 255, 0.35);
}

.primary-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(51, 133, 255, 0.45);
}

.panel {
  margin-top: 20px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid rgba(51, 133, 255, 0.12);
  padding: 18px;
  box-shadow: 0 4px 16px rgba(51, 133, 255, 0.08);
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.panel-title-row h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 18px;
  color: #1E3A5F;
}

.meta {
  font-size: 12px;
  color: #6B8CBE;
}

.skeleton-container {
  display: grid;
  gap: 12px;
}

.skeleton-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8f9fb 0%, #f0f4f8 100%);
  border: 1px solid rgba(51, 133, 255, 0.08);
}

.skeleton-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-title {
  width: 60%;
  height: 18px;
  background: linear-gradient(90deg, #e8eef5 25%, #f0f4f8 50%, #e8eef5 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  border-radius: 4px;
}

.skeleton-desc {
  width: 85%;
  height: 14px;
  background: linear-gradient(90deg, #e8eef5 25%, #f0f4f8 50%, #e8eef5 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  animation-delay: 0.1s;
  border-radius: 4px;
}

.skeleton-meta {
  width: 40%;
  height: 12px;
  background: linear-gradient(90deg, #e8eef5 25%, #f0f4f8 50%, #e8eef5 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  animation-delay: 0.2s;
  border-radius: 4px;
}

.skeleton-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-btn {
  width: 56px;
  height: 28px;
  background: linear-gradient(90deg, #e8eef5 25%, #f0f4f8 50%, #e8eef5 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  border-radius: 8px;
}

@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  border-radius: 16px;
  background: linear-gradient(180deg, #F8FBFF 0%, #F0F7FF 100%);
  text-align: center;
}

.empty-state.error {
  background: linear-gradient(180deg, #FFF8F8 0%, #FFF0F0 100%);
}

.empty-icon {
  color: #B8D4F0;
  margin-bottom: 16px;
}

.empty-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1E3A5F;
}

.empty-desc {
  margin: 0;
  font-size: 14px;
  color: #6B8CBE;
}

.empty-action {
  margin-top: 20px;
}

.retry-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  padding: 10px 20px;
  border: 1px solid rgba(229, 62, 62, 0.3);
  border-radius: 10px;
  background: #fff;
  color: #E53E3E;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.retry-btn:hover {
  background: #FFF5F5;
  border-color: rgba(229, 62, 62, 0.5);
}

.plan-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.plan-card {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  border: 1px solid rgba(51, 133, 255, 0.12);
  border-radius: 16px;
  padding: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #FAFCFF 100%);
  box-shadow: 0 2px 8px rgba(51, 133, 255, 0.05);
  transition: all 0.25s ease;
}

.plan-card:hover {
  border-color: rgba(51, 133, 255, 0.25);
  box-shadow: 0 6px 20px rgba(51, 133, 255, 0.12);
  transform: translateY(-2px);
}

.plan-card:focus {
  outline: 2px solid rgba(51, 133, 255, 0.4);
  outline-offset: 2px;
}

.plan-main {
  flex: 1;
  min-width: 0;
}

.plan-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.plan-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #1E3A5F;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-badge {
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}

.plan-badge.status-new {
  background: linear-gradient(135deg, #E8FFF0 0%, #D4FFEB 100%);
  color: #00A854;
}

.plan-badge.status-recent {
  background: linear-gradient(135deg, #FFF8E6 0%, #FFEDD0 100%);
  color: #D48806;
}

.plan-badge.status-old {
  background: linear-gradient(135deg, #F0F5FA 0%, #E4EBF2 100%);
  color: #6B8CBE;
}

.plan-desc {
  margin: 0 0 10px;
  font-size: 13px;
  color: #3B5998;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.plan-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.plan-meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6B8CBE;
}

.plan-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-radius: 10px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s ease;
  border: none;
}

.action-btn span {
  display: none;
}

@media (min-width: 480px) {
  .action-btn span {
    display: inline;
  }
}

.edit-btn {
  background: #F0F7FF;
  color: #3385FF;
  border: 1px solid rgba(51, 133, 255, 0.2);
}

.edit-btn:hover {
  background: #E0EFFF;
  border-color: rgba(51, 133, 255, 0.4);
}

.delete-btn {
  background: #FFF5F5;
  color: #E53E3E;
  border: 1px solid rgba(229, 62, 62, 0.2);
}

.delete-btn:hover:not(:disabled) {
  background: #FFECEC;
  border-color: rgba(229, 62, 62, 0.4);
}

.delete-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.plan-list-enter-active,
.plan-list-leave-active {
  transition: all 0.3s ease;
}

.plan-list-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.plan-list-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal-content {
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.modal-icon {
  color: #E53E3E;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: #1E3A5F;
}

.modal-message {
  margin: 0 0 20px;
  font-size: 14px;
  color: #3B5998;
  line-height: 1.6;
}

.modal-warning {
  color: #E53E3E;
  font-size: 13px;
}

.modal-actions {
  display: flex;
  gap: 12px;
}

.modal-btn {
  flex: 1;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
}

.cancel-btn {
  background: #F0F4F8;
  color: #3B5998;
}

.cancel-btn:hover {
  background: #E4EBF2;
}

.confirm-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: linear-gradient(135deg, #FF6B6B 0%, #E53E3E 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(229, 62, 62, 0.3);
}

.confirm-btn:hover:not(:disabled) {
  box-shadow: 0 6px 16px rgba(229, 62, 62, 0.4);
}

.confirm-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.modal-enter-active,
.modal-leave-active {
  transition: all 0.25s ease;
}

.modal-enter-from,
.modal-leave-to .modal-content {
  opacity: 0;
  transform: scale(0.95) translateY(10px);
}
</style>
