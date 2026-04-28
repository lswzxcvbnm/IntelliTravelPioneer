<template>
  <div class="cockpit-page">
    <div class="cockpit-header">
      <div>
        <h1>行程作战中心</h1>
        <p class="subtitle">统一管理你的所有 AI 行程方案，快速进入生成、编辑与执行。</p>
      </div>
      <button class="btn-create" @click="goCreate">
        <el-icon><Plus /></el-icon>
        <span>新建作战计划</span>
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <span class="stat-label">行程总数</span>
        <strong class="stat-value">{{ itineraries.length }}</strong>
      </div>
      <div class="stat-card">
        <span class="stat-label">累计天数</span>
        <strong class="stat-value">{{ totalDays }}</strong>
      </div>
      <div class="stat-card">
        <span class="stat-label">预计总预算</span>
        <strong class="stat-value">{{ formatCurrency(totalBudget) }}</strong>
      </div>
    </div>

    <div v-if="errorMessage" class="error-state" role="alert">
      <el-icon :size="24"><WarningFilled /></el-icon>
      <span>{{ errorMessage }}</span>
      <button class="retry-btn" @click="refreshList">
        <el-icon><RefreshRight /></el-icon>
        <span>重试</span>
      </button>
    </div>

    <div v-else-if="isLoading" class="skeleton-list">
      <div v-for="i in 3" :key="i" class="skeleton-row">
        <div class="skel-title"></div>
        <div class="skel-desc"></div>
        <div class="skel-meta"></div>
      </div>
    </div>

    <div v-else-if="itineraries.length === 0" class="empty-page">
      <el-icon :size="48" class="empty-icon"><Document /></el-icon>
      <p class="empty-title">还没有作战计划</p>
      <p class="empty-desc">点击右上角按钮开始创建你的第一个行程计划</p>
    </div>

    <TransitionGroup v-else name="list" tag="div" class="plan-table">
      <div v-for="item in itineraries" :key="item.id" class="plan-row">
        <div class="plan-info">
          <div class="plan-name-row">
            <span class="plan-name">{{ item.title }}</span>
            <span class="plan-badge" :class="getPlanStatus(item).class">{{ getPlanStatus(item).text }}</span>
          </div>
          <span class="plan-summary">{{ getPlanSummary(item.plan) }}</span>
        </div>
        <div class="plan-meta">
          <span>{{ item.plan.totalDays }} 天</span>
          <span>{{ formatCurrency(item.plan.estimatedCost) }}</span>
          <span>{{ formatDate(item.updateTime) }}</span>
        </div>
        <div class="plan-actions">
          <button class="btn-edit" @click="goEdit(item.id)">
            <el-icon><Edit /></el-icon>
            <span>编辑</span>
          </button>
          <button class="btn-delete" @click="openDeleteModal(item)" :disabled="isDeleting">
            <el-icon><Delete /></el-icon>
          </button>
        </div>
      </div>
    </TransitionGroup>

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="showDeleteModal" class="modal-overlay" @click.self="closeDeleteModal">
          <div class="modal-content">
            <div class="modal-header">
              <el-icon class="modal-icon" :size="22"><WarningFilled /></el-icon>
              <h3>确认删除</h3>
            </div>
            <p class="modal-message">确定要删除 <strong>{{ deletingItem?.title }}</strong> 吗？此操作不可恢复。</p>
            <div class="modal-actions">
              <button class="btn-cancel" @click="closeDeleteModal">取消</button>
              <button class="btn-confirm" @click="confirmDelete" :disabled="isDeleting">
                {{ isDeleting ? '删除中...' : '确认删除' }}
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
import { ElMessage } from 'element-plus'
import {
  Plus, Document, WarningFilled, RefreshRight, Edit, Delete
} from '@element-plus/icons-vue'
import { getAllItineraries, getItineraryDetail, deleteItinerary } from '../../api/itinerary'
import type { ItineraryVO, ItineraryPlan } from '../../api/itinerary'

const router = useRouter()

const isLoading = ref(false)
const errorMessage = ref('')
const itineraries = ref<ItineraryVO[]>([])
const showDeleteModal = ref(false)
const deletingItem = ref<ItineraryVO | null>(null)
const isDeleting = ref(false)

const totalDays = computed(() =>
  itineraries.value.reduce((sum, i) => sum + i.plan.totalDays, 0)
)
const totalBudget = computed(() =>
  itineraries.value.reduce((sum, i) => sum + i.plan.estimatedCost, 0)
)

const formatCurrency = (v: number) => `¥${v.toLocaleString('zh-CN')}`

const formatDate = (value: string) => {
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const diff = Math.floor((Date.now() - d.getTime()) / 86400000)
  if (diff === 0) return '今天'
  if (diff === 1) return '昨天'
  if (diff < 7) return `${diff}天前`
  return d.toLocaleDateString('zh-CN')
}

const getPlanSummary = (plan: ItineraryPlan) => {
  const first = plan.dailyPlans[0]?.items[0]
  return first ? `首日推荐：${first.name}（${first.type}）` : '暂无路线项，请前往编辑页完善行程。'
}

const getPlanStatus = (item: ItineraryVO) => {
  const diff = Math.floor((Date.now() - new Date(item.updateTime).getTime()) / 86400000)
  if (diff === 0) return { text: '今日更新', class: 'badge-new' }
  if (diff <= 3) return { text: '近期更新', class: 'badge-recent' }
  return { text: '较早', class: 'badge-old' }
}

const refreshList = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const idRes = await getAllItineraries()
    const ids = idRes.data.data || []
    if (ids.length === 0) { itineraries.value = []; return }
    const detailResults = await Promise.all(ids.map(id => getItineraryDetail(id)))
    itineraries.value = detailResults
      .map(r => r.data.data)
      .sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime())
  } catch {
    errorMessage.value = '行程加载失败，请稍后重试。'
  } finally {
    isLoading.value = false
  }
}

const goCreate = () => router.push('/battle/create')
const goEdit = (id: string) => router.push({ path: '/battle/create', query: { itineraryId: id } })

const openDeleteModal = (item: ItineraryVO) => { deletingItem.value = item; showDeleteModal.value = true }
const closeDeleteModal = () => { if (!isDeleting.value) { showDeleteModal.value = false; deletingItem.value = null } }

const confirmDelete = async () => {
  if (!deletingItem.value) return
  isDeleting.value = true
  try {
    await deleteItinerary(deletingItem.value.id)
    itineraries.value = itineraries.value.filter(i => i.id !== deletingItem.value!.id)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败，请稍后重试')
  } finally {
    isDeleting.value = false
    showDeleteModal.value = false
    deletingItem.value = null
  }
}

onMounted(() => { refreshList() })
</script>

<style scoped>
.cockpit-page {
  padding: 32px 40px;
  max-width: 1200px;
  margin: 0 auto;
  height: 100dvh;
  overflow-y: auto;
}

.cockpit-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 28px;
}

.cockpit-header h1 { font-size: 26px; font-weight: 700; color: #1E3A5F; margin-bottom: 6px; }
.subtitle { color: #6B8CBE; font-size: 14px; margin: 0; }

.btn-create {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 13px 22px;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 4px 14px rgba(51, 133, 255, 0.3);
  flex-shrink: 0;
}

.btn-create:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(51, 133, 255, 0.4); }

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 28px;
}

.stat-card {
  flex: 1;
  padding: 20px 24px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(51, 133, 255, 0.1);
  box-shadow: 0 1px 8px rgba(51, 133, 255, 0.04);
}

.stat-label { font-size: 13px; color: #6B8CBE; font-weight: 500; }
.stat-value { display: block; font-size: 28px; font-weight: 700; color: #1E3A5F; margin-top: 6px; }

.error-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 14px;
  background: #FFF5F5;
  color: #E53E3E;
  font-size: 14px;
  margin-bottom: 20px;
}

.retry-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: 8px 16px;
  border: 1px solid rgba(229, 62, 62, 0.3);
  border-radius: 10px;
  background: #fff;
  color: #E53E3E;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
}

.skeleton-list { display: flex; flex-direction: column; gap: 10px; }
.skeleton-row {
  display: flex; align-items: center; gap: 20px;
  padding: 18px 20px; border-radius: 14px;
  background: linear-gradient(90deg, #f0f4f8 25%, #e8eef5 50%, #f0f4f8 75%);
  background-size: 200% 100%; animation: shimmer 1.5s infinite;
}
.skel-title { width: 200px; height: 18px; background: rgba(0,0,0,0.06); border-radius: 4px; }
.skel-desc { flex: 1; height: 14px; background: rgba(0,0,0,0.04); border-radius: 4px; }
.skel-meta { width: 160px; height: 14px; background: rgba(0,0,0,0.04); border-radius: 4px; }

@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

.empty-page {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 64px 24px; border-radius: 16px;
  background: linear-gradient(180deg, #F8FBFF 0%, #F0F7FF 100%);
}
.empty-icon { color: #B8D4F0; margin-bottom: 16px; }
.empty-title { font-size: 18px; font-weight: 600; color: #1E3A5F; margin-bottom: 8px; }
.empty-desc { font-size: 14px; color: #6B8CBE; }

.plan-table { display: flex; flex-direction: column; gap: 8px; }

.plan-row {
  display: grid;
  grid-template-columns: 1fr 280px 120px;
  align-items: center;
  gap: 16px;
  padding: 18px 24px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid rgba(51, 133, 255, 0.08);
  transition: all 0.2s;
}

.plan-row:hover {
  border-color: rgba(51, 133, 255, 0.2);
  box-shadow: 0 2px 12px rgba(51, 133, 255, 0.08);
}

.plan-info { min-width: 0; }

.plan-name-row {
  display: flex; align-items: center; gap: 10px; margin-bottom: 4px;
}

.plan-name { font-size: 16px; font-weight: 700; color: #1E3A5F; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.plan-badge { padding: 2px 8px; border-radius: 6px; font-size: 11px; font-weight: 600; white-space: nowrap; }
.badge-new { background: #E8FFF0; color: #00A854; }
.badge-recent { background: #FFF8E6; color: #D48806; }
.badge-old { background: #F0F4F8; color: #6B8CBE; }

.plan-summary { font-size: 13px; color: #6B8CBE; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }

.plan-meta {
  display: flex; gap: 20px; font-size: 13px; color: #6B8CBE;
}

.plan-actions { display: flex; gap: 8px; justify-content: flex-end; }

.btn-edit {
  display: flex; align-items: center; gap: 4px;
  padding: 8px 14px; border: none; border-radius: 10px;
  background: #F0F7FF; color: #3385FF; font-size: 13px; font-weight: 500; cursor: pointer;
}
.btn-edit:hover { background: #E0EFFF; }

.btn-delete {
  display: flex; align-items: center; padding: 8px 10px; border: none; border-radius: 10px;
  background: #FFF5F5; color: #E53E3E; cursor: pointer;
}
.btn-delete:hover:not(:disabled) { background: #FFECEC; }
.btn-delete:disabled { opacity: 0.5; cursor: not-allowed; }

.list-enter-active, .list-leave-active { transition: all 0.3s ease; }
.list-enter-from { opacity: 0; transform: translateY(-8px); }
.list-leave-to { opacity: 0; transform: translateX(16px); }

.modal-overlay {
  position: fixed; inset: 0; background: rgba(15,23,42,0.5); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.modal-content {
  width: 400px; background: #fff; border-radius: 20px; padding: 28px;
  box-shadow: 0 20px 50px rgba(0,0,0,0.15);
}
.modal-header { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.modal-icon { color: #E53E3E; }
.modal-header h3 { margin: 0; font-size: 18px; color: #1E3A5F; }
.modal-message { font-size: 14px; color: #3B5998; line-height: 1.6; margin-bottom: 24px; }
.modal-actions { display: flex; gap: 12px; }
.btn-cancel, .btn-confirm {
  flex: 1; padding: 12px; border-radius: 12px; font-size: 14px; font-weight: 600; cursor: pointer; border: none;
}
.btn-cancel { background: #F0F4F8; color: #3B5998; }
.btn-confirm { background: linear-gradient(135deg, #FF6B6B 0%, #E53E3E 100%); color: #fff; }
.modal-enter-active, .modal-leave-active { transition: all 0.2s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; transform: scale(0.95); }
</style>
