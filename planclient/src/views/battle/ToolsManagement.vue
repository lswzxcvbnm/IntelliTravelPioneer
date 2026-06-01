<template>
  <div class="tools-page">
    <div class="tools-header">
      <div>
        <h1>工具管理</h1>
        <p class="subtitle">管理 AI Agent 可用的工具集，查看工具详情和调用历史</p>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <span class="stat-label">工具总数</span>
        <strong class="stat-value">{{ tools.length }}</strong>
      </div>
      <div class="stat-card">
        <span class="stat-label">已启用</span>
        <strong class="stat-value stat-success">{{ enabledCount }}</strong>
      </div>
      <div class="stat-card">
        <span class="stat-label">累计调用</span>
        <strong class="stat-value">{{ totalCallsCount }}</strong>
      </div>
    </div>

    <div v-if="errorMessage" class="error-state" role="alert">
      <el-icon :size="24"><WarningFilled /></el-icon>
      <span>{{ errorMessage }}</span>
      <button class="retry-btn" @click="fetchTools">
        <el-icon><RefreshRight /></el-icon>
        <span>重试</span>
      </button>
    </div>

    <div v-else-if="isLoading" class="skeleton-list">
      <div v-for="i in 4" :key="i" class="skeleton-row">
        <div class="skel-title"></div>
        <div class="skel-desc"></div>
      </div>
    </div>

    <template v-else>
      <div class="filter-bar">
        <button
          v-for="cat in categories"
          :key="cat.value"
          class="filter-chip"
          :class="{ active: activeCategory === cat.value }"
          @click="activeCategory = cat.value"
        >
          {{ cat.label }}
        </button>
      </div>

      <TransitionGroup name="list" tag="div" class="tools-grid">
        <div v-for="tool in filteredTools" :key="tool.toolName" class="tool-card" @click="openDetail(tool)">
          <div class="tool-card-header">
            <div class="tool-icon-wrapper" :class="tool.userEnabled ? 'active' : 'disabled'">
              <span class="tool-icon">{{ getToolIcon(tool.toolName) }}</span>
            </div>
            <div class="tool-name-section">
              <span class="tool-display-name">{{ tool.displayName }}</span>
              <span class="tool-category-badge">{{ tool.category }}</span>
            </div>
            <el-switch
              :model-value="tool.userEnabled"
              :loading="togglingTool === tool.toolName"
              @click.stop
              @change="handleToggle(tool)"
              size="small"
            />
          </div>
          <p class="tool-desc">{{ tool.description || '暂无描述' }}</p>
          <div class="tool-stats">
            <span class="tool-stat-item">
              调用 <strong>{{ tool.totalCalls }}</strong> 次
            </span>
            <span class="tool-stat-item" v-if="tool.totalCalls > 0">
              成功率 <strong>{{ calcRate(tool.successCalls, tool.totalCalls) }}%</strong>
            </span>
          </div>
        </div>
      </TransitionGroup>
    </template>

    <Teleport to="body">
      <div v-if="showDetail" class="modal-overlay" @click.self="closeDetail">
        <div class="detail-panel">
          <div class="detail-header">
            <h2>{{ detailTool?.displayName }}</h2>
            <button class="close-btn" @click="closeDetail">✕</button>
          </div>

          <div class="detail-tabs">
            <button
              :class="{ active: detailTab === 'info' }"
              @click="detailTab = 'info'"
            >工具详情</button>
            <button
              :class="{ active: detailTab === 'history' }"
              @click="detailTab = 'history'; fetchCallHistory()"
            >调用历史</button>
          </div>

          <div v-if="detailTab === 'info'" class="detail-body">
            <div class="detail-section">
              <h3>基本信息</h3>
              <div class="detail-grid">
                <div class="detail-item">
                  <label>工具名称</label>
                  <code>{{ detailTool?.toolName }}</code>
                </div>
                <div class="detail-item">
                  <label>分类</label>
                  <span>{{ detailTool?.category }}</span>
                </div>
                <div class="detail-item">
                  <label>状态</label>
                  <el-switch
                    :model-value="detailTool?.userEnabled ?? false"
                    :loading="togglingTool === detailTool?.toolName"
                    @change="handleToggle(detailTool!)"
                    size="small"
                  />
                </div>
              </div>
            </div>

            <div class="detail-section">
              <h3>功能描述</h3>
              <p class="detail-desc">{{ detailTool?.description || '暂无描述' }}</p>
            </div>

            <div class="detail-section">
              <h3>参数说明</h3>
              <div class="params-table" v-if="parsedParams.length > 0">
                <div v-for="(p, idx) in parsedParams" :key="idx" class="param-row">
                  <code class="param-name">{{ p.name }}</code>
                  <span class="param-type">{{ p.type }}</span>
                  <span v-if="p.required" class="param-required">必填</span>
                  <span v-else class="param-optional">可选</span>
                  <span class="param-desc">{{ p.description }}</span>
                </div>
              </div>
              <p v-else class="no-data">暂无参数信息</p>
            </div>

            <div class="detail-section" v-if="detailTool?.usageExample">
              <h3>使用示例</h3>
              <pre class="usage-example">{{ detailTool?.usageExample }}</pre>
            </div>

            <div class="detail-section">
              <h3>调用统计</h3>
              <div class="call-stats">
                <div class="call-stat-card">
                  <strong>{{ detailTool?.totalCalls ?? 0 }}</strong>
                  <span>总调用次数</span>
                </div>
                <div class="call-stat-card">
                  <strong>{{ detailTool?.successCalls ?? 0 }}</strong>
                  <span>成功调用</span>
                </div>
                <div class="call-stat-card">
                  <strong>{{ calcRate(detailTool?.successCalls ?? 0, detailTool?.totalCalls ?? 0) }}%</strong>
                  <span>成功率</span>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="detail-body">
            <div v-if="historyLoading" class="skeleton-list">
              <div v-for="i in 3" :key="i" class="skeleton-row">
                <div class="skel-title"></div>
                <div class="skel-desc"></div>
              </div>
            </div>

            <div v-else-if="historyError" class="error-state">
              <span>{{ historyError }}</span>
              <button class="retry-btn" @click="fetchCallHistory">重试</button>
            </div>

            <div v-else-if="callHistory.length === 0" class="empty-state-small">
              <p>暂无调用记录</p>
            </div>

            <TransitionGroup v-else name="list" tag="div" class="history-list">
              <div v-for="record in callHistory" :key="record.id" class="history-item">
                <div class="history-left">
                  <span class="history-status" :class="record.success ? 'status-ok' : 'status-fail'">
                    {{ record.success ? '✓' : '✗' }}
                  </span>
                  <div class="history-info">
                    <span class="history-tool">{{ record.displayName }}</span>
                    <span class="history-result">{{ record.resultSummary || (record.success ? '执行成功' : '执行失败') }}</span>
                  </div>
                </div>
                <div class="history-right">
                  <span class="history-time">{{ formatTime(record.callTime) }}</span>
                  <span v-if="record.itineraryId" class="history-itinerary" @click.stop="goToItinerary(record.itineraryId)">
                    查看行程
                  </span>
                </div>
              </div>
            </TransitionGroup>

            <div class="pagination-row" v-if="historyTotal > historySize">
              <button
                class="page-btn"
                :disabled="historyPage <= 0"
                @click="historyPage--; fetchCallHistory()"
              >上一页</button>
              <span class="page-info">第 {{ historyPage + 1 }} / {{ Math.ceil(historyTotal / historySize) }} 页</span>
              <button
                class="page-btn"
                :disabled="(historyPage + 1) * historySize >= historyTotal"
                @click="historyPage++; fetchCallHistory()"
              >下一页</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { WarningFilled, RefreshRight } from '@element-plus/icons-vue'
import {
  getToolList,
  toggleTool,
  getToolCallHistory,
  type ToolVO,
  type ToolCallHistoryVO
} from '../../api/tools'

const router = useRouter()

const tools = ref<ToolVO[]>([])
const isLoading = ref(false)
const errorMessage = ref('')
const activeCategory = ref('all')
const togglingTool = ref<string | null>(null)

const showDetail = ref(false)
const detailTool = ref<ToolVO | null>(null)
const detailTab = ref<'info' | 'history'>('info')

const callHistory = ref<ToolCallHistoryVO[]>([])
const historyLoading = ref(false)
const historyError = ref('')
const historyPage = ref(0)
const historySize = 20
const historyTotal = ref(0)

const categories = [
  { label: '全部', value: 'all' },
  { label: '🗺️ 地图', value: '地图' },
  { label: '📚 知识库', value: '知识库' }
]

const enabledCount = computed(() => tools.value.filter(t => t.userEnabled).length)
const totalCallsCount = computed(() => tools.value.reduce((sum, t) => sum + t.totalCalls, 0))

const filteredTools = computed(() => {
  if (activeCategory.value === 'all') return tools.value
  return tools.value.filter(t => t.category === activeCategory.value)
})

const parsedParams = computed(() => {
  if (!detailTool.value?.parametersJson) return []
  try {
    return JSON.parse(detailTool.value.parametersJson) as Array<{
      name: string; type: string; required: boolean; description: string
    }>
  } catch {
    return []
  }
})

const getToolIcon = (name: string): string => {
  const iconMap: Record<string, string> = {
    maps_weather: '🌤️',
    maps_text_search: '🔍',
    maps_around_search: '📍',
    maps_search_detail: '📋',
    maps_direction_driving: '🚗',
    maps_direction_walking: '🚶',
    maps_direction_bicycling: '🚲',
    maps_direction_transit_integrated: '🚌',
    maps_distance: '📏',
    maps_geo: '🗺️',
    maps_regeocode: '🏠',
    maps_ip_location: '🌐',
    maps_schema_personal_map: '🖼️',
    maps_schema_navi: '🧭',
    maps_schema_take_taxi: '🚕',
    search_destination_info: '📚'
  }
  return iconMap[name] || '🔧'
}

const calcRate = (success: number, total: number): number => {
  if (total === 0) return 100
  return Math.round((success / total) * 100)
}

const formatTime = (value: string): string => {
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString('zh-CN')
}

const fetchTools = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const res = await getToolList()
    tools.value = (res.data.data || []) as ToolVO[]
  } catch (e: any) {
    errorMessage.value = e?.message || '工具列表加载失败，请稍后重试'
  } finally {
    isLoading.value = false
  }
}

const handleToggle = async (tool: ToolVO) => {
  togglingTool.value = tool.toolName
  try {
    const res = await toggleTool(tool.toolName)
    const updated = res.data.data as ToolVO
    const idx = tools.value.findIndex(t => t.toolName === tool.toolName)
    if (idx >= 0) tools.value[idx] = updated
    if (detailTool.value?.toolName === tool.toolName) {
      detailTool.value = updated
    }
    ElMessage.success(updated.userEnabled ? `已启用「${tool.displayName}」` : `已禁用「${tool.displayName}」`)
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    togglingTool.value = null
  }
}

const openDetail = (tool: ToolVO) => {
  detailTool.value = { ...tool }
  detailTab.value = 'info'
  showDetail.value = true
}

const closeDetail = () => {
  showDetail.value = false
  detailTool.value = null
}

const fetchCallHistory = async () => {
  historyLoading.value = true
  historyError.value = ''
  try {
    const res = await getToolCallHistory(
      detailTool.value?.toolName,
      historyPage.value,
      historySize
    )
    const data = res.data.data as any
    callHistory.value = data.content || []
    historyTotal.value = data.totalElements || 0
  } catch (e: any) {
    historyError.value = e?.message || '调用历史加载失败'
  } finally {
    historyLoading.value = false
  }
}

const goToItinerary = (itineraryId: string) => {
  closeDetail()
  router.push({ path: '/battle/create', query: { itineraryId } })
}

onMounted(() => {
  fetchTools()
})
</script>

<style scoped>
.tools-page {
  padding: 28px 32px;
  max-width: 1200px;
  margin: 0 auto;
}

.tools-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.tools-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: #1E3A5F;
  margin: 0 0 6px;
}

.subtitle {
  color: #7A8FA6;
  font-size: 14px;
  margin: 0;
}

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 14px;
  padding: 16px 20px;
  border: 1px solid rgba(51, 133, 255, 0.08);
  box-shadow: 0 2px 8px rgba(51, 133, 255, 0.04);
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #7A8FA6;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #1E3A5F;
}

.stat-success {
  color: #22C55E;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.filter-chip {
  padding: 8px 18px;
  border-radius: 20px;
  border: 1.5px solid rgba(51, 133, 255, 0.15);
  background: #fff;
  color: #5B7FA5;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.filter-chip:hover {
  border-color: #3385FF;
  color: #3385FF;
}

.filter-chip.active {
  background: rgba(51, 133, 255, 0.1);
  border-color: #3385FF;
  color: #1A6FFF;
  font-weight: 600;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.tool-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  border: 1px solid rgba(51, 133, 255, 0.08);
  box-shadow: 0 2px 8px rgba(51, 133, 255, 0.04);
  cursor: pointer;
  transition: all 0.2s;
}

.tool-card:hover {
  border-color: rgba(51, 133, 255, 0.2);
  box-shadow: 0 4px 16px rgba(51, 133, 255, 0.08);
  transform: translateY(-2px);
}

.tool-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.tool-icon-wrapper {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.tool-icon-wrapper.active {
  background: rgba(51, 133, 255, 0.1);
}

.tool-icon-wrapper.disabled {
  background: rgba(0, 0, 0, 0.04);
  filter: grayscale(0.6);
}

.tool-name-section {
  flex: 1;
  min-width: 0;
}

.tool-display-name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #1E3A5F;
}

.tool-category-badge {
  display: inline-block;
  font-size: 11px;
  color: #3385FF;
  background: rgba(51, 133, 255, 0.08);
  padding: 1px 8px;
  border-radius: 8px;
  margin-top: 2px;
}

.tool-desc {
  font-size: 13px;
  color: #7A8FA6;
  line-height: 1.5;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tool-stats {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #94A3B8;
}

.tool-stat-item strong {
  color: #5B7FA5;
}

.error-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px;
  background: #FFF5F5;
  border-radius: 12px;
  color: #E53E3E;
  font-size: 14px;
}

.retry-btn {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border: 1px solid #E53E3E;
  border-radius: 8px;
  background: transparent;
  color: #E53E3E;
  font-size: 13px;
  cursor: pointer;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-row {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid rgba(51, 133, 255, 0.06);
}

.skel-title {
  height: 16px;
  width: 40%;
  background: linear-gradient(90deg, #E8F0FE 0%, #F0F5FF 50%, #E8F0FE 100%);
  border-radius: 6px;
  animation: shimmer 1.5s infinite;
  margin-bottom: 8px;
}

.skel-desc {
  height: 12px;
  width: 70%;
  background: linear-gradient(90deg, #E8F0FE 0%, #F0F5FF 50%, #E8F0FE 100%);
  border-radius: 6px;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: -200px 0; }
  100% { background-position: 200px 0; }
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 999;
  padding: 24px;
}

.detail-panel {
  background: #fff;
  border-radius: 18px;
  width: 100%;
  max-width: 620px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
}

.detail-header h2 {
  font-size: 17px;
  font-weight: 700;
  color: #1E3A5F;
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  font-size: 18px;
  color: #7A8FA6;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}

.close-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #1E3A5F;
}

.detail-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
  padding: 0 24px;
}

.detail-tabs button {
  padding: 12px 20px;
  border: none;
  background: none;
  font-size: 14px;
  color: #7A8FA6;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
}

.detail-tabs button.active {
  color: #1A6FFF;
  border-bottom-color: #1A6FFF;
  font-weight: 600;
}

.detail-body {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-section h3 {
  font-size: 13px;
  font-weight: 600;
  color: #7A8FA6;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0 0 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item label {
  font-size: 12px;
  color: #94A3B8;
}

.detail-item code {
  font-size: 13px;
  background: #F4F6F8;
  padding: 4px 8px;
  border-radius: 6px;
  word-break: break-all;
}

.detail-desc {
  font-size: 14px;
  color: #5B7FA5;
  line-height: 1.6;
  margin: 0;
}

.params-table {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #F8FAFD;
  border-radius: 8px;
  flex-wrap: wrap;
}

.param-name {
  font-size: 13px;
  font-weight: 600;
  color: #1E3A5F;
}

.param-type {
  font-size: 11px;
  color: #7A8FA6;
  background: #EAEEF3;
  padding: 2px 6px;
  border-radius: 4px;
}

.param-required {
  font-size: 11px;
  color: #E53E3E;
  background: #FEE2E2;
  padding: 2px 6px;
  border-radius: 4px;
}

.param-optional {
  font-size: 11px;
  color: #7A8FA6;
  background: #F1F5F9;
  padding: 2px 6px;
  border-radius: 4px;
}

.param-desc {
  font-size: 12px;
  color: #94A3B8;
  width: 100%;
}

.usage-example {
  background: #F8FAFD;
  border-radius: 10px;
  padding: 14px 16px;
  font-size: 13px;
  color: #5B7FA5;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.call-stats {
  display: flex;
  gap: 12px;
}

.call-stat-card {
  flex: 1;
  text-align: center;
  padding: 14px;
  background: #F8FAFD;
  border-radius: 10px;
}

.call-stat-card strong {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #1E3A5F;
  margin-bottom: 2px;
}

.call-stat-card span {
  font-size: 12px;
  color: #94A3B8;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #F8FAFD;
  border-radius: 10px;
}

.history-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.history-status {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.history-status.status-ok {
  background: #DCFCE7;
  color: #16A34A;
}

.history-status.status-fail {
  background: #FEE2E2;
  color: #DC2626;
}

.history-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.history-tool {
  font-size: 13px;
  font-weight: 600;
  color: #1E3A5F;
}

.history-result {
  font-size: 12px;
  color: #94A3B8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
  margin-left: 12px;
}

.history-time {
  font-size: 11px;
  color: #94A3B8;
}

.history-itinerary {
  font-size: 11px;
  color: #3385FF;
  cursor: pointer;
}

.history-itinerary:hover {
  text-decoration: underline;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
}

.page-btn {
  padding: 6px 14px;
  border: 1px solid rgba(51, 133, 255, 0.15);
  border-radius: 8px;
  background: #fff;
  color: #3385FF;
  font-size: 13px;
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: #7A8FA6;
}

.no-data {
  font-size: 13px;
  color: #94A3B8;
  margin: 0;
}

.empty-state-small {
  text-align: center;
  padding: 32px 16px;
  color: #94A3B8;
  font-size: 14px;
}

.list-enter-active,
.list-leave-active {
  transition: all 0.3s ease;
}

.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
