<template>
  <div class="customize-shell">
    <div class="customize-main">
      <header class="top-bar">
        <button type="button" class="back-btn" @click="goBack">← 返回</button>
        <h1>{{ isEditMode ? '编辑行程计划' : '创建行程计划' }}</h1>
      </header>

      <div class="content-scroll" ref="contentScrollRef">
        <section class="card" v-if="step === 1">
          <h2>输入行程需求</h2>
          <div class="form-cols">
            <label class="input-card input-card-entry">
              <span class="label-text">📍 目的地</span>
              <input v-model.trim="request.destination" placeholder="想去哪里探索？" class="large-input" />
            </label>
            <div class="row-group">
              <label class="input-card half">
                <span class="label-text">📅 天数</span>
                <div class="stepper">
                  <button class="step-btn" @click="request.days = Math.max(1, request.days - 1)">−</button>
                  <span class="step-val">{{ request.days }}</span>
                  <button class="step-btn" @click="request.days = Math.min(30, request.days + 1)">+</button>
                </div>
              </label>
              <label class="input-card half">
                <span class="label-text">💰 预算</span>
                <div class="segment-group">
                  <div v-for="opt in [{l:'经济',v:'low'},{l:'舒适',v:'medium'},{l:'豪华',v:'high'}]" :key="opt.v"
                    class="segment-item" :class="{active: request.budgetLevel === opt.v}"
                    @click="request.budgetLevel = opt.v">{{ opt.l }}</div>
                </div>
              </label>
            </div>
            <div class="input-card full-width">
              <span class="label-text">✨ 兴趣偏好</span>
              <div class="chips-container">
                <button v-for="item in interestOptions" :key="item.value" class="chip-pill"
                  :class="[{ active: request.interests.includes(item.value) }, `chip-${item.value}`]"
                  @click="toggleInterest(item.value)">{{ item.label }}</button>
              </div>
            </div>
            <div class="input-card input-card-entry">
              <span class="label-text">✏️ 补充说明</span>
              <textarea v-model.trim="request.additionalNotes" rows="3"
                placeholder="例如：希望每天傍晚安排轻松步行，避开高峰景点" />
            </div>
          </div>
          <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
          <div class="form-actions">
            <button class="secondary" @click="jumpToExisting" v-if="isEditMode">加载已有</button>
            <button class="primary" @click="handleGenerate" :disabled="isLoadingGenerate">
              {{ isLoadingGenerate ? '正在生成...' : '✨ 生成 AI 方案' }}
            </button>
          </div>
        </section>

        <section class="card" v-if="step === 2">
          <h2>方案编辑</h2>
          <p class="hint">你可以自行增添，删除或调整行程，或者将你的想法告诉AI，让它帮你完善行程。</p>
          <div v-if="!editablePlan" class="empty">尚未生成或加载方案。</div>
          <template v-else>
            <div class="ai-refine-box">
              <label>
                <strong class="ai-refine-title">AI 行程完善</strong>
                <textarea v-model="refinePrompt" rows="3"
                  placeholder="告诉AI你想如何修改，例如：'把第一天的景点换成室内的'、'晚餐都安排在酒店附近'" />
              </label>
              <div class="actions ai-refine-actions">
                <button class="primary mini" @click="handleAiRefine" :disabled="isRefining || !refinePrompt.trim()">
                  {{ isRefining ? 'AI 修改中...' : '发送给 AI 进行修改' }}
                </button>
              </div>
            </div>
            <label>计划标题 <input v-model.trim="editablePlan.title" placeholder="请输入计划标题" /></label>
            <div class="form-grid compact">
              <label>预计花费 <input v-model.number="editablePlan.estimatedCost" type="number" min="0" /></label>
              <label>总天数 <input v-model.number="editablePlan.totalDays" type="number" min="1" max="30" /></label>
            </div>
            <div class="timeline-view">
              <div class="day-section" v-for="day in editablePlan.dailyPlans" :key="day.dayNumber"
                @dragover.prevent @drop="onDayDrop(day.dayNumber)">
                <div class="day-sticky-header">
                  <div class="day-badge-pill">
                    <span class="day-num">DAY {{ day.dayNumber }}</span>
                  </div>
                  <button class="icon-btn-add" @click="addItem(day.dayNumber)">+</button>
                </div>
                <div class="timeline-list">
                  <div class="timeline-row" v-for="(item, idx) in day.items" :key="`${day.dayNumber}-${idx}`"
                    draggable="true" @dragstart="onDragStart(day.dayNumber, idx)"
                    @dragover.prevent @drop.stop="onDrop(day.dayNumber, idx)">
                    <div class="timeline-thread">
                      <div class="node-circle" :class="`bg-${getTypeClass(item.type)}`">{{ getTypeIcon(item.type) }}</div>
                    </div>
                    <div class="timeline-card-wrap">
                      <div class="timeline-card">
                        <div class="card-header-row">
                          <div class="card-info">
                            <div class="card-title">{{ item.name || '未命名路线项' }}</div>
                            <div class="card-desc">{{ item.description || '暂无描述' }}</div>
                          </div>
                          <span class="drag-handle">⋮⋮</span>
                        </div>
                        <div class="card-actions-row">
                          <button class="text-btn" @click="toggleItemEditor(day.dayNumber, idx)">
                            {{ isItemEditing(day.dayNumber, idx) ? '收起' : '编辑' }}</button>
                          <button class="text-btn danger" @click="removeItem(day.dayNumber, idx)">删除</button>
                        </div>
                        <div v-if="isItemEditing(day.dayNumber, idx)" class="embedded-editor">
                          <div class="field-row">
                            <select v-model="item.type" class="mini-select">
                              <option v-for="t in routeTypeOptions" :key="t" :value="t">{{ t }}</option>
                            </select>
                            <input v-model.trim="item.name" placeholder="名称" class="mini-input" />
                          </div>
                          <textarea v-model.trim="item.description" rows="2" placeholder="描述..." class="mini-textarea" />
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-if="day.items.length === 0" class="empty-day-placeholder">拖拽或点击+号添加行程</div>
                </div>
              </div>
            </div>
            <div class="form-actions">
              <button class="secondary" @click="step = 1">返回上一页</button>
              <button class="primary" @click="savePlan" :disabled="!editablePlan || isSaving">
                {{ isSaving ? '保存中...' : isEditMode ? '更新方案' : '保存方案' }}
              </button>
            </div>
          </template>
        </section>

        <section class="card" v-if="step === 3">
          <h2>行程详情预览</h2>
          <p class="hint">方案已保存，你可以继续编辑或返回列表查看。</p>
          <div class="saved-preview">
            <div class="saved-preview-header"><h3>行程详情预览</h3><span class="saved-tag">已保存</span></div>
            <div class="saved-overview">
              <p><strong>标题：</strong>{{ editablePlan?.title || '(未命名)' }}</p>
              <p><strong>总天数：</strong>{{ editablePlan?.totalDays || 0 }} 天</p>
              <p><strong>预算：</strong>{{ formatCurrency(editablePlan?.estimatedCost || 0) }}</p>
              <p><strong>路线项：</strong>{{ routeItemCount }} 个</p>
            </div>
            <div class="preview-days">
              <div class="preview-day" v-for="day in editablePlan?.dailyPlans" :key="`pv-${day.dayNumber}`">
                <p class="preview-day-title">第 {{ day.dayNumber }} 天</p>
                <ul>
                  <li v-for="(item, i) in day.items" :key="i">
                    <span>{{ getTypeIcon(item.type) }}</span><span>{{ item.name }}</span>
                  </li>
                </ul>
              </div>
            </div>
            <div class="form-actions">
              <button class="secondary" @click="continueEditing">继续编辑</button>
              <button class="primary" @click="goBackToList">返回列表</button>
            </div>
          </div>
        </section>
      </div>
    </div>

    <aside class="agent-panel" v-if="(step === 1 && isLoadingGenerate) || (step === 2 && isRefining)">
      <div class="agent-panel-header">
        <span class="agent-panel-icon">🤖</span>
        <span>Agent 思考过程</span>
        <span class="agent-panel-badge active">运行中</span>
      </div>

      <div class="phase-indicator">
        <div class="phase-steps">
          <div class="phase-step" :class="{ active: currentAgentPhase === 'PLAN_START' || currentAgentPhase === 'PLAN_END', done: isPhaseDone('PLAN_END', currentAgentPhase) }">
            <span class="phase-dot">1</span><span class="phase-label">规划</span>
          </div>
          <div class="phase-line" :class="{ filled: isPhaseDone('PLAN_END', currentAgentPhase) }"></div>
          <div class="phase-step" :class="{ active: currentAgentPhase === 'EXECUTE_START' || currentAgentPhase === 'EXECUTE_END', done: isPhaseDone('EXECUTE_END', currentAgentPhase) }">
            <span class="phase-dot">2</span><span class="phase-label">执行</span>
          </div>
          <div class="phase-line" :class="{ filled: isPhaseDone('EXECUTE_END', currentAgentPhase) }"></div>
          <div class="phase-step" :class="{ active: currentAgentPhase === 'VERIFY_START' || currentAgentPhase === 'VERIFY_END', done: currentAgentPhase === 'VERIFY_END' }">
            <span class="phase-dot">3</span><span class="phase-label">验证</span>
          </div>
        </div>
      </div>

      <div class="agent-log-stream" ref="agentLogRef">
        <div v-for="(entry, idx) in activeAgentLogs" :key="idx" class="log-row">
          <div v-if="entry.type === 'phase'" class="phase-banner" :class="{ 'phase-done': entry.phaseType?.endsWith('_END') }">
            <span class="phase-icon">{{ entry.phaseType?.endsWith('_END') ? '✓' : '▶' }}</span>
            <span class="phase-msg">{{ entry.message }}</span>
          </div>
          <div v-else-if="entry.type === 'thought'" class="thought-bubble">
            <span class="thought-icon">💭</span>
            <span class="thought-text">{{ entry.message }}</span>
          </div>
          <div v-else-if="entry.type === 'tool'" class="tool-call-row">
            <span class="tool-icon">🔧</span>
            <span class="tool-text">{{ entry.message }}</span>
          </div>
        </div>
        <div class="stream-cursor" v-if="activeAgentLogs.length > 0">▋</div>
      </div>

      <div class="agent-panel-empty" v-if="activeAgentLogs.length === 0">
        <p>等待 Agent 开始思考...</p>
      </div>
    </aside>

    <aside class="agent-panel agent-panel-idle" v-else-if="step === 1 && !isLoadingGenerate">
      <div class="agent-panel-header">
        <span class="agent-panel-icon">🤖</span>
        <span>Agent 面板</span>
        <span class="agent-panel-badge idle">待机中</span>
      </div>
      <div class="agent-panel-idle-content">
        <div class="idle-icon">✨</div>
        <p>填写左侧表单后点击<br /><strong>"生成 AI 方案"</strong><br />Agent 将在这里展示<br />实时规划与思考过程</p>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  generateItineraryStream,
  getItineraryDetail,
  saveItinerary,
  updateItinerary
} from '../../api/itinerary'
import type { ItineraryPlan, ItineraryRequest, PhaseData } from '../../api/itinerary'
import { goBackByAppRule } from '../../composables/useBackHandler'

const router = useRouter()
const route = useRoute()
const goBack = () => goBackByAppRule(router)

interface LogEntry {
  type: 'phase' | 'thought' | 'tool'
  message: string
  phaseType?: string
}

const step = ref(1)
const isLoadingGenerate = ref(false)
const generatingLogs = ref<LogEntry[]>([])
const currentPhase = ref('')
const isSaving = ref(false)
const errorMessage = ref('')
const editablePlan = ref<ItineraryPlan | null>(null)
const savedItineraryId = ref('')
const contentScrollRef = ref<HTMLElement | null>(null)
const agentLogRef = ref<HTMLElement | null>(null)

const refinePrompt = ref('')
const isRefining = ref(false)
const refineLogs = ref<LogEntry[]>([])
const refineCurrentPhase = ref('')

const itineraryId = computed(() => String(route.query.itineraryId || ''))
const isEditMode = computed(() => itineraryId.value.length > 0)

const currentAgentPhase = computed(() => {
  if (isLoadingGenerate.value) return currentPhase.value
  if (isRefining.value) return refineCurrentPhase.value
  return ''
})

const activeAgentLogs = computed(() => {
  if (isLoadingGenerate.value) return generatingLogs.value
  if (isRefining.value) return refineLogs.value
  return []
})

watch(activeAgentLogs, async () => {
  await nextTick()
  if (agentLogRef.value) {
    agentLogRef.value.scrollTop = agentLogRef.value.scrollHeight
  }
})

const toolNameMapping: Record<string, string> = {
  maps_direction_bicycling: '正在规划骑行路线...',
  maps_direction_driving: '正在规划驾车路线...',
  maps_direction_transit_integrated: '正在规划公共交通路线...',
  maps_direction_walking: '正在规划步行路线...',
  maps_distance: '正在测量距离...',
  maps_geo: '正在解析地址坐标...',
  maps_regeocode: '正在获取地址信息...',
  maps_ip_location: '正在进行定位...',
  maps_schema_personal_map: '正在生成地图展示链接...',
  maps_around_search: '正在搜索周边信息...',
  maps_search_detail: '正在查询地点详情...',
  maps_text_search: '正在搜索相关地点...',
  maps_schema_navi: '正在生成导航链接...',
  maps_schema_take_taxi: '正在生成打车链接...',
  maps_weather: '正在查询天气情况...',
  search_destination_info: '正在查询目的地旅行信息...'
}

const getLogMessage = (msg: string) => {
  const invokeMatch = msg.match(/Invoking tool: (\w+)/)
  if (invokeMatch && invokeMatch[1]) {
    return toolNameMapping[invokeMatch[1]] || msg
  }
  const completeMatch = msg.match(/Tool (\w+) completed/)
  if (completeMatch && completeMatch[1]) {
    const name = toolNameMapping[completeMatch[1]]
    if (name) return name.replace('正在', '已完成').replace('...', '。')
  }
  return toolNameMapping[msg] || msg
}

const phaseOrder = ['PLAN_START', 'PLAN_END', 'EXECUTE_START', 'EXECUTE_END', 'VERIFY_START', 'VERIFY_END']
const isPhaseDone = (target: string, cur: string) => phaseOrder.indexOf(cur) > phaseOrder.indexOf(target)

const interestOptions = [
  { label: '历史文化', value: 'history' },
  { label: '美食探索', value: 'food' },
  { label: '自然徒步', value: 'nature' },
  { label: '城市漫游', value: 'citywalk' },
  { label: '夜游体验', value: 'night' }
]

const DEFAULT_ROUTE_TYPE = '景点'
const routeTypeOptions = [DEFAULT_ROUTE_TYPE, '美食', '交通', '住宿', '休闲', '购物']
const normalizeRouteType = (v: string) => routeTypeOptions.includes(v) ? v : DEFAULT_ROUTE_TYPE

const request = reactive<ItineraryRequest>({
  destination: Array.isArray(route.query.destination) ? String(route.query.destination[0] || '') : String(route.query.destination || ''),
  days: 3, interests: [], budgetLevel: 'medium', additionalNotes: ''
})

const draggingItem = ref<{ dayNumber: number; index: number } | null>(null)
const editingItemKey = ref('')
const routeItemCount = computed(() => {
  if (!editablePlan.value) return 0
  return editablePlan.value.dailyPlans.reduce((sum, day) => sum + day.items.length, 0)
})
const formatCurrency = (v: number) => `￥${v.toLocaleString('zh-CN')}`

const toggleInterest = (v: string) => {
  const exists = request.interests.includes(v)
  request.interests = exists ? request.interests.filter(i => i !== v) : [...request.interests, v]
}

const validateRequest = () => {
  if (!request.destination?.trim()) return '想去哪里探索？请先输入你的目的地。'
  if (request.days < 1 || request.days > 30) return '出行天数需在 1-30 之间。'
  return ''
}

const normalizePlan = (plan: ItineraryPlan): ItineraryPlan => ({
  title: plan.title || `${request.destination || '我的'}行程计划`,
  estimatedCost: Number(plan.estimatedCost || 0),
  totalDays: Number(plan.totalDays || plan.dailyPlans?.length || 1),
  dailyPlans: plan.dailyPlans.map((day, index) => ({
    dayNumber: day.dayNumber || index + 1,
    items: day.items.map(item => ({
      type: normalizeRouteType(item.type),
      name: item.name || '未命名路线项',
      description: item.description || '暂无描述',
      location: item.location || { lat: 0, lng: 0, name: '' }
    }))
  }))
})

const handleGenerate = async () => {
  errorMessage.value = ''
  generatingLogs.value = []
  currentPhase.value = ''
  const ve = validateRequest(); if (ve) { errorMessage.value = ve; return }
  isLoadingGenerate.value = true
  try {
    await generateItineraryStream(
      { ...request },
      (phase: PhaseData) => { generatingLogs.value.push({ type: 'phase', message: phase.message, phaseType: phase.type }); currentPhase.value = phase.type },
      (msg: string) => { generatingLogs.value.push({ type: 'thought', message: msg }) },
      (msg: string) => { generatingLogs.value.push({ type: 'tool', message: getLogMessage(msg) }) },
      (plan) => { editablePlan.value = normalizePlan(plan); step.value = 2; isLoadingGenerate.value = false },
      (error) => { errorMessage.value = error.message || '生成报错，请稍后重试。'; isLoadingGenerate.value = false }
    )
  } catch (error: any) { errorMessage.value = '生成失败，请稍后重试。'; isLoadingGenerate.value = false }
}

const handleAiRefine = async () => {
  if (!refinePrompt.value.trim() || !editablePlan.value) return
  isRefining.value = true; refineLogs.value = []; refineCurrentPhase.value = ''
  const refineRequest: ItineraryRequest = {
    ...request,
    additionalNotes: `【注意事项：用户需要修改这份已有计划】\n当前已有计划：\n${JSON.stringify(editablePlan.value)}\n\n用户修改建议：\n${refinePrompt.value}`
  }
  try {
    await generateItineraryStream(refineRequest,
      (phase: PhaseData) => { refineLogs.value.push({ type: 'phase', message: phase.message, phaseType: phase.type }); refineCurrentPhase.value = phase.type },
      (msg: string) => { refineLogs.value.push({ type: 'thought', message: msg }) },
      (msg) => { refineLogs.value.push({ type: 'tool', message: getLogMessage(msg) }) },
      (plan) => { editablePlan.value = normalizePlan(plan); isRefining.value = false; refinePrompt.value = '' },
      (error) => { ElMessageBox.alert(error.message || '修改报错', 'AI修改失败', { type: 'error' }); isRefining.value = false }
    )
  } catch (error: any) { ElMessageBox.alert(error.message || '修改失败', 'AI修改失败', { type: 'error' }); isRefining.value = false }
}

const addItem = (dn: number) => {
  const day = editablePlan.value?.dailyPlans.find(d => d.dayNumber === dn)
  if (!day) return
  day.items.push({ type: DEFAULT_ROUTE_TYPE, name: '新路线项', description: '请补充该路线项描述', location: { lat: 0, lng: 0, name: '' } })
}
const removeItem = (dn: number, idx: number) => {
  const day = editablePlan.value?.dailyPlans.find(d => d.dayNumber === dn)
  day?.items.splice(idx, 1)
  if (editingItemKey.value === `${dn}-${idx}`) editingItemKey.value = ''
}
const toggleItemEditor = (dn: number, idx: number) => {
  const k = `${dn}-${idx}`; editingItemKey.value = editingItemKey.value === k ? '' : k
}
const isItemEditing = (dn: number, idx: number) => editingItemKey.value === `${dn}-${idx}`
const getTypeIcon = (t: string) => ({ '景点': '🗺', '美食': '🍜', '交通': '🚌', '住宿': '🏨', '休闲': '🌿', '购物': '🛍' }[t] || '📍')
const getTypeClass = (t: string) => ({ '景点': 'spot', '美食': 'food', '交通': 'traffic', '住宿': 'hotel', '休闲': 'relax', '购物': 'shopping' }[t] || 'default')

const onDragStart = (dn: number, idx: number) => { draggingItem.value = { dayNumber: dn, index: idx } }
const onDrop = (tdn: number, tidx: number) => {
  if (!editablePlan.value || !draggingItem.value) return
  const s = draggingItem.value
  const sd = editablePlan.value.dailyPlans.find(d => d.dayNumber === s.dayNumber)
  const td = editablePlan.value.dailyPlans.find(d => d.dayNumber === tdn)
  if (!sd || !td) { draggingItem.value = null; return }
  const m = sd.items[s.index]
  if (!m) { draggingItem.value = null; return }
  if (s.dayNumber === tdn) {
    sd.items.splice(s.index, 1)
    sd.items.splice(s.index < tidx ? tidx - 1 : tidx, 0, m)
  } else {
    sd.items.splice(s.index, 1); td.items.splice(tidx, 0, m)
  }
  draggingItem.value = null
}
const onDayDrop = (tdn: number) => {
  if (!editablePlan.value || !draggingItem.value) return
  const s = draggingItem.value
  const sd = editablePlan.value.dailyPlans.find(d => d.dayNumber === s.dayNumber)
  const td = editablePlan.value.dailyPlans.find(d => d.dayNumber === tdn)
  if (!sd || !td || !sd.items[s.index]) { draggingItem.value = null; return }
  const m = sd.items[s.index]
  if (s.dayNumber === tdn) { if (s.index < sd.items.length - 1) { sd.items.splice(s.index, 1); sd.items.push(m) } }
  else { sd.items.splice(s.index, 1); td.items.push(m) }
  draggingItem.value = null
}

const jumpToExisting = () => { if (isEditMode.value) loadDetailForEdit() }
const loadDetailForEdit = async () => {
  if (!itineraryId.value) return
  isLoadingGenerate.value = true; errorMessage.value = ''
  try {
    const r = await getItineraryDetail(itineraryId.value)
    editablePlan.value = normalizePlan(r.data.data.plan)
    savedItineraryId.value = r.data.data.id; step.value = 2
  } catch { errorMessage.value = '加载已有计划失败。' } finally { isLoadingGenerate.value = false }
}

const validatePlan = () => {
  if (!editablePlan.value) return '请先生成或加载行程方案。'
  if (!editablePlan.value.title.trim()) return '计划标题不能为空。'
  if (editablePlan.value.totalDays < 1) return '总天数必须大于 0。'
  return ''
}

const savePlan = async () => {
  errorMessage.value = ''; const ve = validatePlan(); if (ve) { errorMessage.value = ve; return }
  if (!editablePlan.value) return
  isSaving.value = true
  try {
    if (isEditMode.value) {
      await updateItinerary(itineraryId.value, { ...editablePlan.value, destination: request.destination.trim() })
      savedItineraryId.value = itineraryId.value; step.value = 3
    } else {
      const r = await saveItinerary({ ...editablePlan.value, destination: request.destination.trim(), dailyPlans: editablePlan.value.dailyPlans })
      savedItineraryId.value = r.data.data; step.value = 3
    }
  } catch { errorMessage.value = '保存失败，请稍后重试。' } finally { isSaving.value = false }
}

const continueEditing = () => { step.value = 2 }
const goBackToList = () => { router.push('/battle') }

onMounted(async () => {
  if (route.query.destination) request.destination = String(route.query.destination)
  if (itineraryId.value) await loadDetailForEdit()
})
</script>

<style scoped>
.customize-shell {
  display: flex;
  height: 100dvh;
  overflow: hidden;
}

.customize-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.top-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 32px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  flex-shrink: 0;
}

.top-bar h1 {
  font-size: 20px;
  font-weight: 700;
  color: #1E3A5F;
  margin: 0;
}

.back-btn {
  padding: 8px 16px;
  border-radius: 10px;
  border: 1px solid rgba(51, 133, 255, 0.15);
  background: #fff;
  color: #3385FF;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.back-btn:hover {
  background: #F0F7FF;
}

.content-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px 40px;
}

.card {
  border-radius: 16px;
  border: 1px solid rgba(51, 133, 255, 0.08);
  background: #fff;
  padding: 28px;
  box-shadow: 0 1px 8px rgba(51, 133, 255, 0.04);
  margin-bottom: 20px;
}

.card h2 {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 700;
  color: #1E3A5F;
}

.form-cols {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 20px;
}

.row-group {
  display: flex;
  gap: 12px;
}

.input-card {
  background: #FAFCFF;
  border-radius: 14px;
  padding: 16px;
  border: 1px solid rgba(51, 133, 255, 0.1);
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.label-text {
  font-size: 13px;
  color: #6B8CBE;
  font-weight: 600;
}

.large-input {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1E3A5F;
  width: 100%;
}

.stepper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  background: #fff;
  border-radius: 10px;
  padding: 4px;
}

.step-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: #fff;
  font-size: 18px;
  color: #3385FF;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.step-val {
  font-size: 16px;
  font-weight: 700;
  color: #1E3A5F;
}

.segment-group {
  display: flex;
  background: #fff;
  border-radius: 10px;
  padding: 4px;
  width: 100%;
  gap: 4px;
}

.segment-item {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  color: #6B8CBE;
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.segment-item.active {
  background: #3385FF;
  color: #fff;
  box-shadow: 0 2px 8px rgba(51, 133, 255, 0.3);
}

.chips-container {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chip-pill {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  border: 1px solid transparent;
  background: #fff;
  color: #6B8CBE;
  cursor: pointer;
  transition: all 0.2s;
}

.chip-pill:hover {
  background: #f0f4f8;
}

.chip-pill.active {
  background: #3385FF;
  color: #fff;
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(51, 133, 255, 0.3);
}

textarea {
  width: 100%;
  border: none;
  background: transparent;
  padding: 0;
  resize: vertical;
  min-height: 60px;
  font-family: inherit;
  color: #1E3A5F;
  font-size: 14px;
  line-height: 1.6;
}

.input-card:focus-within {
  border-color: #3385FF;
  box-shadow: 0 0 0 3px rgba(51, 133, 255, 0.1);
}

.error-text {
  color: #E53E3E;
  font-size: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #FFF5F5;
  border-left: 4px solid #E53E3E;
  margin-bottom: 16px;
  font-weight: 500;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.primary, .secondary, .mini {
  border-radius: 12px;
  border: none;
  padding: 14px 28px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.primary {
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  flex: 1;
  box-shadow: 0 4px 14px rgba(51, 133, 255, 0.3);
}

.primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(51, 133, 255, 0.4);
}

.primary:disabled {
  background: #93c5fd;
  cursor: not-allowed;
  box-shadow: none;
}

.secondary {
  background: #F0F4F8;
  color: #1E3A5F;
  flex: 1;
}

.secondary:hover {
  background: #E4EBF2;
}

.mini {
  background: #F0F4F8;
  color: #1E3A5F;
  padding: 10px 18px;
  font-size: 14px;
}

.hint {
  font-size: 14px;
  color: #6B8CBE;
  line-height: 1.6;
  padding: 14px 16px;
  border-radius: 12px;
  background: #EBF5FF;
  border-left: 4px solid #3385FF;
  margin-bottom: 20px;
}

.ai-refine-box {
  background: #F8FBFF;
  border-radius: 14px;
  padding: 20px;
  margin-bottom: 24px;
  border: 1px solid rgba(51, 133, 255, 0.1);
}

.ai-refine-actions {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.empty {
  border: 2px dashed rgba(51, 133, 255, 0.15);
  border-radius: 14px;
  padding: 48px;
  text-align: center;
  color: #6B8CBE;
  font-size: 15px;
  background: #FAFCFF;
}

.timeline-view {
  padding-bottom: 20px;
}

.day-section {
  margin-bottom: 32px;
}

.day-sticky-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.day-badge-pill {
  background: #3385FF;
  color: #fff;
  padding: 8px 18px;
  border-radius: 20px;
  font-weight: 800;
  font-size: 14px;
  box-shadow: 0 3px 10px rgba(51, 133, 255, 0.3);
}

.icon-btn-add {
  background: #fff;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid rgba(51, 133, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3385FF;
  font-size: 20px;
  font-weight: bold;
  cursor: pointer;
}

.timeline-list {
  padding-left: 12px;
  border-left: 2px dashed rgba(51, 133, 255, 0.1);
  margin-left: 20px;
  padding-top: 8px;
}

.timeline-row {
  position: relative;
  margin-bottom: 20px;
}

.timeline-thread {
  position: absolute;
  left: -32px;
  top: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.node-circle {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: #fff;
  border: 3px solid #F0F4F8;
  z-index: 5;
}

.timeline-card-wrap {
  margin-left: 20px;
}

.timeline-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(51, 133, 255, 0.06);
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.card-title {
  font-weight: 700;
  font-size: 15px;
  color: #1E3A5F;
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: #6B8CBE;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-actions-row {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(51, 133, 255, 0.1);
}

.text-btn {
  background: none;
  border: none;
  font-size: 13px;
  font-weight: 600;
  color: #3385FF;
  cursor: pointer;
  padding: 3px 6px;
}

.text-btn.danger {
  color: #6B8CBE;
}

.text-btn.danger:hover {
  color: #E53E3E;
}

.drag-handle {
  color: #cbd5e1;
  padding: 4px;
  cursor: grab;
  font-size: 18px;
}

.embedded-editor {
  margin-top: 10px;
  background: #FAFCFF;
  padding: 12px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-row {
  display: flex;
  gap: 8px;
}

.mini-select, .mini-input, .mini-textarea {
  background: #fff;
  border: 1px solid rgba(51, 133, 255, 0.1);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 14px;
  width: 100%;
  font-family: inherit;
  color: #1E3A5F;
}

.mini-select {
  flex: 0 0 80px;
}

.empty-day-placeholder {
  font-size: 13px;
  color: #6B8CBE;
  text-align: center;
  padding: 20px;
  border: 2px dashed rgba(51, 133, 255, 0.1);
  border-radius: 12px;
  margin-left: 20px;
}

.saved-preview {
  border: 1px solid rgba(51, 133, 255, 0.1);
  border-radius: 16px;
  padding: 24px;
}

.saved-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
}

.saved-preview-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.saved-tag {
  border-radius: 20px;
  background: #dcfce7;
  color: #166534;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 700;
}

.saved-overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 20px;
  background: #FAFCFF;
  padding: 16px;
  border-radius: 12px;
}

.saved-overview p {
  margin: 0;
  font-size: 14px;
  color: #6B8CBE;
}

.saved-overview strong {
  color: #1E3A5F;
  font-weight: 600;
}

.preview-days {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-day {
  border: 1px solid rgba(51, 133, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
}

.preview-day-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
  color: #3385FF;
}

.preview-day ul {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-day li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  padding: 8px 12px;
  background: #FAFCFF;
  border-radius: 8px;
}

.bg-spot { background: #e0f2fe; color: #0284c7; }
.bg-food { background: #ffedd5; color: #ea580c; }
.bg-traffic { background: #f3e8ff; color: #7c3aed; }
.bg-hotel { background: #dbeafe; color: #2563eb; }
.bg-relax { background: #dcfce7; color: #16a34a; }
.bg-shopping { background: #fce7f3; color: #db2777; }
.bg-default { background: #f1f5f9; color: #475569; }

/* ============ Agent Panel ============ */

.agent-panel {
  width: 380px;
  min-width: 380px;
  height: 100dvh;
  border-left: 1px solid rgba(51, 133, 255, 0.1);
  background: linear-gradient(180deg, #ffffff 0%, #FAFCFF 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.agent-panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 20px 16px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
}

.agent-panel-icon {
  font-size: 22px;
}

.agent-panel-header span:nth-child(2) {
  font-size: 16px;
  font-weight: 700;
  color: #1E3A5F;
  flex: 1;
}

.agent-panel-badge {
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
}

.agent-panel-badge.active {
  background: #E8FFF0;
  color: #00A854;
  animation: badge-pulse 2s infinite;
}

.agent-panel-badge.idle {
  background: #F0F4F8;
  color: #6B8CBE;
}

@keyframes badge-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.phase-indicator {
  padding: 16px 20px 0;
}

.phase-steps {
  display: flex;
  align-items: center;
  justify-content: center;
}

.phase-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.phase-dot {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
  background: #e2e8f0;
  color: #94a3b8;
  transition: all 0.3s;
}

.phase-step.active .phase-dot {
  background: #3385FF;
  color: #fff;
  box-shadow: 0 0 0 4px rgba(51, 133, 255, 0.2);
  animation: pulse-dot 1.5s infinite;
}

.phase-step.done .phase-dot {
  background: #10b981;
  color: #fff;
}

.phase-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
}

.phase-step.active .phase-label,
.phase-step.done .phase-label {
  color: #1E3A5F;
}

.phase-line {
  width: 44px;
  height: 2px;
  background: #e2e8f0;
  margin-bottom: 16px;
  transition: all 0.3s;
}

.phase-line.filled {
  background: #10b981;
}

@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 0 4px rgba(51, 133, 255, 0.2); }
  50% { box-shadow: 0 0 0 8px rgba(51, 133, 255, 0.1); }
}

.agent-log-stream {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.log-row {
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.phase-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(51, 133, 255, 0.06);
  border-left: 3px solid #3385FF;
  font-weight: 600;
  font-size: 13px;
  color: #1E3A5F;
}

.phase-banner.phase-done {
  background: rgba(16, 185, 129, 0.06);
  border-left-color: #10b981;
  color: #065f46;
}

.phase-icon {
  font-size: 14px;
}

.thought-bubble {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(139, 92, 246, 0.05);
  border: 1px dashed rgba(139, 92, 246, 0.15);
  margin-left: 6px;
}

.thought-bubble .thought-icon {
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 1px;
}

.thought-bubble .thought-text {
  font-size: 13px;
  line-height: 1.5;
  color: #4c1d95;
}

.tool-call-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 8px;
  background: #fff;
  margin-left: 6px;
  font-size: 12px;
  color: #6B8CBE;
}

.tool-call-row .tool-icon {
  font-size: 14px;
}

.stream-cursor {
  color: #3385FF;
  font-size: 14px;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.agent-panel-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94A3B8;
  font-size: 14px;
}

.agent-panel-idle-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  text-align: center;
}

.idle-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.agent-panel-idle-content p {
  font-size: 14px;
  color: #6B8CBE;
  line-height: 1.8;
}

.agent-panel-idle-content strong {
  color: #3385FF;
}
</style>
