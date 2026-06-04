<template>
  <div class="workspace">
    <!-- 顶部导航栏 -->
    <header class="ws-header">
      <button class="ws-back-btn" @click="goBack">← 返回</button>
      <h2 class="ws-header-title">{{ editablePlan?.title || pageTitle }}</h2>
      <div class="ws-header-actions">
        <button v-if="editablePlan" class="ws-action-btn" @click="handleSave" :disabled="isSaving">
          {{ isSaving ? '保存中...' : '保存' }}
        </button>
      </div>
    </header>

    <!-- 主体区域 -->
    <div class="ws-body">
      <!-- 左侧：对话区域 -->
      <div class="ws-conversation">
        <div class="ws-messages" ref="messagesRef">
          <!-- 空状态 / 欢迎卡片 -->
          <div v-if="messages.length === 0" class="ws-welcome">
            <div class="ws-welcome-icon">🚀</div>
            <h3>AI 行程规划工作台</h3>
            <p>描述你的旅行需求，AI 将为你生成完整行程方案，并可在右侧画布中实时编辑。</p>
            <div class="ws-suggested-prompts">
              <button
                v-for="(sp, i) in suggestedPrompts"
                :key="i"
                class="ws-sp-card"
                @click="handleSend(sp)"
              >
                <span class="ws-sp-icon">{{ spIcons[i] }}</span>
                <span class="ws-sp-text">{{ sp }}</span>
              </button>
            </div>
          </div>

          <!-- 消息列表 -->
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['ws-msg', msg.role]"
          >
            <!-- 用户消息 -->
            <template v-if="msg.role === 'user'">
              <div class="ws-avatar user-avatar">👤</div>
              <div class="ws-bubble-wrapper">
                <div class="ws-bubble user">{{ msg.content }}</div>
                <span class="ws-time">{{ formatTime(msg.time) }}</span>
              </div>
            </template>

            <!-- AI 助手消息 -->
            <template v-if="msg.role === 'assistant'">
              <div class="ws-avatar assistant-avatar">🤖</div>
              <div class="ws-bubble-wrapper">
                <div class="ws-bubble assistant">
                  <!-- 文本内容 -->
                  <div v-if="msg.content" class="ws-bubble-text" v-html="formatContent(msg.content)"></div>

                  <!-- 内联工具调用卡片 -->
                  <ToolCallCard
                    v-if="msg.toolCalls && parseToolType(msg.toolCalls) === 'tools'"
                    :toolCalls="msg.toolCalls"
                  />

                  <!-- 流式生成指示器 -->
                  <div v-if="msg.streaming" class="ws-streaming">
                    <div class="ws-streaming-header">
                      <span class="ws-streaming-dots">
                        <span></span><span></span><span></span>
                      </span>
                      <span>{{ currentAction || 'AI 正在思考...' }}</span>
                    </div>

                    <!-- 阶段进度条 -->
                    <div v-if="isGenerating || isRefining" class="ws-mini-progress">
                      <div class="ws-phase" :class="{ active: currentPhase.includes('PLAN') }">规划</div>
                      <div class="ws-phase-sep"></div>
                      <div class="ws-phase" :class="{ active: currentPhase.includes('EXECUTE') }">执行</div>
                      <div class="ws-phase-sep"></div>
                      <div class="ws-phase" :class="{ active: currentPhase.includes('VERIFY') }">验证</div>
                    </div>

                    <!-- 实时日志（可折叠） -->
                    <details v-if="liveLogs.length > 0" class="ws-live-logs" open>
                      <summary>查看实时日志 ({{ liveLogs.length }})</summary>
                      <div class="ws-log-list">
                        <div
                          v-for="(log, li) in liveLogs.slice(-50)"
                          :key="li"
                          class="ws-log-entry"
                          :class="log.type"
                        >
                          <span class="ws-log-icon">{{ log.icon }}</span>
                          <span class="ws-log-text">{{ log.text }}</span>
                        </div>
                      </div>
                    </details>
                  </div>
                </div>
                <span class="ws-time">{{ formatTime(msg.time) }}</span>
              </div>
            </template>
          </div>
        </div>

        <!-- 底部输入区域（双模式：表单 / 对话） -->
        <div class="ws-input-area">
          <!-- 模式切换标签（仅无行程时显示） -->
          <div v-if="!editablePlan" class="ws-mode-tabs">
            <button
              v-for="m in inputModes"
              :key="m.value"
              class="ws-mode-tab"
              :class="{ active: inputMode === m.value }"
              @click="inputMode = m.value"
            >
              <span class="ws-mode-icon">{{ m.icon }}</span>
              {{ m.label }}
            </button>
          </div>

          <!-- ====== 模式 A: 结构化表单 ====== -->
          <FormInput
            v-if="inputMode === 'form' && !editablePlan"
            :isGenerating="isGenerating"
            @submit="handleFormSubmit"
            @switchMode="inputMode = $event"
          />

          <!-- ====== 模式 B: 自然语言对话 ====== -->
          <ChatInput
            v-else
            :placeholder="inputPlaceholder"
            :disabled="isGenerating || isRefining"
            :isGenerating="isGenerating"
            :isRefining="isRefining"
            :hasPlan="!!editablePlan"
            :showHint="true"
            :mode="inputMode"
            @send="handleSend"
            @switchMode="inputMode = $event"
          />
        </div>
      </div>

      <!-- 右侧：行程画布 -->
      <aside class="ws-canvas">
        <div class="ws-canvas-header">
          <h3>行程画布</h3>
        </div>

        <div class="ws-canvas-body">
          <!-- 空状态 -->
          <div v-if="!editablePlan" class="ws-canvas-empty">
            <div class="ws-canvas-empty-icon">🗺️</div>
            <p>生成行程后，这里将显示可编辑的行程画布</p>
          </div>

          <!-- 编辑态：行程内容 -->
          <template v-else>
            <!-- 标题（可编辑） -->
            <input
              v-model="editablePlan.title"
              class="ws-plan-title"
              placeholder="行程标题"
            />

            <!-- 元信息行 -->
            <div class="ws-plan-meta">
              <span>📅 {{ editablePlan.totalDays }} 天</span>
              <span>💰 ¥{{ editablePlan.estimatedCost || 0 }}</span>
              <span>📍 {{ totalItems }} 个地点</span>
            </div>

            <!-- 天数区块 -->
            <div
              v-for="day in editablePlan.dailyPlans"
              :key="day.dayNumber"
              class="ws-day-section"
              @dragover.prevent
              @drop="onDayDrop(day.dayNumber)"
            >
              <div class="ws-day-header">
                <span class="ws-day-num">{{ day.dayNumber }}</span>
                <span class="ws-day-label">Day {{ day.dayNumber }}</span>
              </div>

              <TransitionGroup name="item-list" tag="div" class="ws-items">
                <div
                  v-for="(item, ii) in day.items"
                  :key="`${day.dayNumber}-${ii}`"
                  class="ws-item"
                  :class="['type-' + getTypeClass(item.type), { expanded: isItemExpanded(day.dayNumber, ii) }]"
                >
                  <div class="ws-item-header" @click="toggleItemExpand(day.dayNumber, ii)">
                    <span class="ws-item-icon">{{ typeIconMap[item.type] || '📍' }}</span>
                    <span class="ws-item-name">{{ item.name || '未命名' }}</span>
                    <span class="ws-item-expand-arrow">{{ isItemExpanded(day.dayNumber, ii) ? '▲' : '▼' }}</span>
                    <div class="ws-item-actions">
                      <button @click.stop="handleDeleteItem(day.dayNumber, ii, item.name)" class="ws-item-btn delete">删除</button>
                    </div>
                  </div>

                  <!-- 可展开详情 -->
                  <transition name="item-expand">
                    <div v-if="isItemExpanded(day.dayNumber, ii)" class="ws-item-detail">
                      <div v-if="item.description && item.description !== '暂无描述'" class="ws-item-detail-row">
                        <span class="ws-item-detail-icon">📝</span>
                        <span class="ws-item-detail-text">{{ item.description }}</span>
                      </div>
                      <div v-if="item.location?.name" class="ws-item-detail-row">
                        <span class="ws-item-detail-icon">📍</span>
                        <span class="ws-item-detail-text">{{ item.location.name }}</span>
                      </div>
                      <div v-if="hasValidCoords(item.location)" class="ws-item-detail-row">
                        <span class="ws-item-detail-icon">🌐</span>
                        <span class="ws-item-detail-text">{{ item.location.lat }}, {{ item.location.lng }}</span>
                      </div>
                      <div v-if="hasValidCoords(item.location)" class="ws-item-map-row">
                        <a
                          class="ws-item-map-link"
                          :href="`https://uri.amap.com/marker?position=${item.location.lng},${item.location.lat}`"
                          target="_blank"
                          rel="noopener"
                        >
                          🗺️ 在地图中查看
                        </a>
                      </div>
                    </div>
                  </transition>
                </div>
              </TransitionGroup>

              <div v-if="day.items.length === 0" class="ws-empty-day">
                拖拽或点击「添加」安排今日行程
              </div>
            </div>

            <!-- 保存按钮 -->
            <button
              class="primary ws-save-btn"
              @click="handleSave"
              :disabled="isSaving"
            >
              {{ isSaving ? '保存中...' : '💾 保存行程' }}
            </button>
          </template>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateItineraryStream, modifyItineraryStream, saveItinerary, updateItinerary } from '../../api/itinerary'
import type { ItineraryRequest, ItineraryPlan, GenerationStats } from '../../api/itinerary'
import { linkItinerary } from '../../api/conversation'
import { goBackByAppRule } from '../../composables/useBackHandler'
import { useConversation } from '../../composables/useConversation'
import { useItinerary } from '../../composables/useItinerary'
import { useDragDrop } from '../../composables/useDragDrop'
import ToolCallCard from './ToolCallCard.vue'
import ChatInput from './ChatInput.vue'
import FormInput from './FormInput.vue'

const router = useRouter()
const route = useRoute()
const goBack = () => goBackByAppRule(router)

// ===== 初始化 composables =====
const {
  messages, messagesRef, addUserMessage, addAssistantMessage,
  updateMessage, appendToMessage, finalizeMessage, scrollToBottom,
  formatContent, formatTime, loadConversationHistory, isLoadingHistory,
} = useConversation()

const {
  editablePlan, isSaving, itineraryId, convId, totalItems,
  normalizePlan, mergeRefinedPlan, saveFromWorkspace, loadDetailForEdit,
  deleteItem,
} = useItinerary()

const { onDragStart, onDrop, onDayDrop } = useDragDrop(editablePlan)

// ===== 建议提示词 =====
const suggestedPrompts = [
  '帮我规划南京3天历史Citywalk之旅',
  '北京5天深度游，预算5000',
  '杭州2天一家大小，带小孩的优选路线',
]
const spIcons = ['🏛', '💼', '😂']

// ===== 页面标题 =====
const pageTitle = computed(() => {
  if (route.query.itineraryId) return '编辑行程'
  return '新建规划'
})

// ===== 生成状态 =====
const isGenerating = ref(false)
const isRefining = ref(false)
const currentAction = ref('')
const currentPhase = ref('')
const liveLogs = ref<Array<{ type: string; icon: string; text: string }>>([])
const activeStats = ref<GenerationStats | null>(null)

// ===== 输入模式（表单 / 对话） =====
// 如果是延续对话（有 convId），默认使用对话模式
const inputMode = ref<'form' | 'chat'>(route.query.convId ? 'chat' : 'form')

const inputModes = [
  { value: 'form' as const, label: '表单输入', icon: '📋' },
  { value: 'chat' as const, label: '对话描述', icon: '💬' },
]

// ===== 计算属性 =====
const inputPlaceholder = computed(() =>
  editablePlan.value ? '描述你想如何修改行程...' : '描述你的旅行目的地和需求...'
)

// ===== 工具调用解析 =====
function parseToolType(toolCallsStr: string | null): 'itinerary' | 'tools' | null {
  if (!toolCallsStr) return null
  try {
    const parsed = JSON.parse(toolCallsStr)
    if (Array.isArray(parsed) && parsed.length > 0) return 'tools'
    return null
  } catch {
    return null
  }
}

// ===== 用户请求解析 =====
function extractDestination(text: string): string {
  const cityPatterns = [
    /北京|上海|南京|杭州|成都|西安|重庆|武汉|长沙|苏州|天津|青岛|大连|宁波|厦门|深圳|广州|佛山/,
    /[一-龥]{2,4}(?:市|)/,
  ]
  for (const p of cityPatterns) {
    const m = text.match(p)
    if (m) return m[0].replace(/市$/, '')
  }
  return ''
}

function extractDays(text: string): number | null {
  const m = text.match(/(\d+)天/)
  return m ? parseInt(m[1]) : null
}

function extractBudget(text: string): string {
  if (/经济|低预算/.test(text)) return 'low'
  if (/舒适/.test(text)) return 'medium'
  if (/豪华|高预算/.test(text)) return 'high'
  return 'medium'
}

function extractInterests(text: string): string[] {
  const interests: string[] = []
  if (/历史|文化|博物馆/.test(text)) interests.push('history')
  if (/美食|小吃|饮品/.test(text)) interests.push('food')
  if (/自然|徒步|山/.test(text)) interests.push('nature')
  if (/城市|Citywalk|citywalk/.test(text)) interests.push('citywalk')
  if (/夜游|夜景|夜间/.test(text)) interests.push('night')
  return interests
}

function parseUserRequest(text: string): ItineraryRequest {
  return {
    destination: extractDestination(text) || '未指定',
    days: extractDays(text) || 3,
    budgetLevel: extractBudget(text),
    interests: extractInterests(text),
    additionalNotes: text,
  }
}

function buildRequestFromPlan(): ItineraryRequest {
  return {
    destination: editablePlan.value?.destination || '',
    days: editablePlan.value?.totalDays || 3,
    budgetLevel: 'medium',
    interests: [],
    additionalNotes: '',
  }
}

// ===== 核心发送逻辑 =====
async function handleSend(text: string) {
  if (!text || isGenerating.value || isRefining.value) return

  addUserMessage(text)
  const assistMsgId = addAssistantMessage()
  await scrollToBottom()

  if (!editablePlan.value) {
    await runGenerate(assistMsgId, text)
  } else {
    await runRefine(assistMsgId, text)
  }
}

// ===== 表单提交逻辑 =====
async function handleFormSubmit(request: {
  destination: string
  days: number
  budgetLevel: string
  interests: string[]
  additionalNotes: string
}) {
  if (!request.destination || isGenerating.value) return

  const budgetLabels: Record<string, string> = { low: '经济', medium: '舒适', high: '豪华' }
  const interestLabels = request.interests.join('、')
  const summaryText = [
    `规划${request.days}天${request.destination}之旅`,
    `预算：${budgetLabels[request.budgetLevel] || '舒适'}`,
    interestLabels ? `兴趣：${interestLabels}` : '',
    request.additionalNotes ? `备注：${request.additionalNotes}` : '',
  ].filter(Boolean).join('；')

  addUserMessage(summaryText)
  const assistMsgId = addAssistantMessage()
  await scrollToBottom()

  isGenerating.value = true
  liveLogs.value = []
  currentPhase.value = ''

  try {
    await generateItineraryStream(
      request,
      (phase) => {
        currentPhase.value = phase.type
        addLog('phase', '📋', phase.message)
      },
      (msg) => {
        appendToMessage(assistMsgId, `\n${msg}`)
        addLog('thought', '💡', msg.slice(0, 120))
      },
      (msg) => {
        addLog('tool', '🔧', msg)
        currentAction.value = extractActionFromLog(msg)
      },
      (plan) => {
        editablePlan.value = normalizePlan(plan)
      },
      (errMsg: any) => {
        const safeMsg = errMsg?.message || errMsg || '未知错误'
        updateMessage(assistMsgId, { content: `❌ ${safeMsg}`, streaming: false })
      },
      (stats) => {
        activeStats.value = stats
        if (stats.toolCalls?.length) {
          updateMessage(assistMsgId, { toolCalls: JSON.stringify(stats.toolCalls) })
        }
      },
      (cid) => { convId.value = cid }
    )
    finalizeMessage(assistMsgId)
  } catch (e: any) {
    updateMessage(assistMsgId, { content: `生成失败: ${e?.message || '未知错误'}`, streaming: false })
  } finally {
    isGenerating.value = false
    inputMode.value = 'chat'
  }
}

// ===== SSE 流式生成 =====
async function runGenerate(msgId: string, userPrompt: string) {
  isGenerating.value = true
  liveLogs.value = []
  currentPhase.value = ''

  try {
    const request = parseUserRequest(userPrompt)

    await generateItineraryStream(
      request,
      (phase) => {
        currentPhase.value = phase.type
        addLog('phase', '📋', phase.message)
      },
      (msg) => {
        appendToMessage(msgId, `\n${msg}`)
        addLog('thought', '💡', msg.slice(0, 120))
      },
      (msg) => {
        addLog('tool', '🔧', msg)
        currentAction.value = extractActionFromLog(msg)
      },
      (plan) => {
        editablePlan.value = normalizePlan(plan)
      },
      (error) => {
        updateMessage(msgId, { content: `❌ 生成失败: ${error.message || '未知错误'}`, streaming: false })
      },
      (stats) => {
        activeStats.value = stats
      },
      (cid) => { convId.value = cid }
    )

    finalizeMessage(msgId)
  } catch (e: any) {
    updateMessage(msgId, { content: `❌ 生成失败: ${e?.message || '未知错误'}`, streaming: false })
  } finally {
    isGenerating.value = false
  }
}

async function runRefine(msgId: string, userPrompt: string) {
  isRefining.value = true
  liveLogs.value = []

  try {
    const originalPlan = { ...editablePlan.value } as ItineraryPlan

    await modifyItineraryStream(
      buildRequestFromPlan(),
      editablePlan.value!,
      userPrompt,
      convId.value,
      (phase) => {
        currentPhase.value = phase.type
        addLog('phase', '📋', phase.message)
      },
      (msg) => {
        appendToMessage(msgId, `\n${msg}`)
        addLog('thought', '💡', msg.slice(0, 120))
      },
      (msg) => {
        addLog('tool', '🔧', msg)
        currentAction.value = extractActionFromLog(msg)
      },
      (plan) => {
        editablePlan.value = mergeRefinedPlan(originalPlan, normalizePlan(plan))
      },
      (error) => {
        updateMessage(msgId, { content: `❌ 优化失败: ${error.message || '未知错误'}`, streaming: false })
      },
      (stats) => {
        activeStats.value = stats
      },
      (cid) => { convId.value = cid }
    )

    finalizeMessage(msgId)
  } catch (e: any) {
    updateMessage(msgId, { content: `❌ 优化失败: ${e?.message || '未知错误'}`, streaming: false })
  } finally {
    isRefining.value = false
  }
}

// ===== 辅助方法 =====
function addLog(type: string, icon: string, text: string) {
  liveLogs.value.push({ type, icon, text: text.slice(0, 200) })
}

/** 自动保存行程并关联到对话 */
async function autoSaveAndLinkItinerary() {
  console.log('[Workspace] autoSaveAndLinkItinerary called - editablePlan:', !!editablePlan.value, 'convId:', convId.value, 'itineraryId:', itineraryId.value)
  
  if (!editablePlan.value || !convId.value) {
    console.log('[Workspace] autoSaveAndLinkItinerary skipped - missing editablePlan or convId')
    return
  }

  try {
    const itineraryData = {
      title: editablePlan.value.title,
      estimatedCost: editablePlan.value.estimatedCost,
      totalDays: editablePlan.value.totalDays,
      destination: editablePlan.value.destination || '',
      dailyPlans: editablePlan.value.dailyPlans,
    }

    if (itineraryId.value) {
      // 更新已有行程
      console.log('[Workspace] updating existing itinerary:', itineraryId.value)
      await updateItinerary(itineraryId.value, itineraryData)
      console.log('[Workspace] itinerary updated successfully')
    } else {
      // 创建新行程
      console.log('[Workspace] creating new itinerary...')
      const res = await saveItinerary(itineraryData)
      const newItineraryId = res.data.data
      console.log('[Workspace] itinerary created, newItineraryId:', newItineraryId)
      
      if (newItineraryId) {
        itineraryId.value = newItineraryId
        // 关联行程到对话
        console.log('[Workspace] linking itinerary to conversation...')
        await linkItinerary(convId.value, newItineraryId)
        console.log('[Workspace] auto saved itinerary and linked to conversation:', newItineraryId)
      }
    }
  } catch (e) {
    console.warn('[Workspace] auto save itinerary failed:', e)
  }
}

function extractActionFromLog(msg: string): string {
  const invokeMatch = msg.match(/Invoking tool: (\w+)/)
  if (invokeMatch && invokeMatch[1]) {
    const nameMap: Record<string, string> = {
      maps_direction_bicycling: '正在规划骑行路线...',
      maps_direction_driving: '正在规划驾车路线...',
      maps_direction_transit_integrated: '正在规划公交交通路线...',
      maps_direction_walking: '正在规划步行路线...',
      maps_distance: '正在测量距离...',
      maps_geo: '正在解析坐标...',
      maps_regeocode: '正在获取地址信息...',
      maps_ip_location: '正在进行定位...',
      maps_schema_personal_map: '正在生成地图展示链接...',
      maps_around_search: '正在搜索周边信息...',
      maps_search_detail: '正在查询地点详情...',
      maps_text_search: '正在搜索相关地点...',
      maps_schema_navi: '正在生成导航链接...',
      maps_schema_take_taxi: '正在生成打车链接...',
      maps_weather: '正在查询天气情况...',
      search_destination_info: '正在查询目的地旅行信息...',
    }
    return nameMap[invokeMatch[1]] || msg
  }
  const completeMatch = msg.match(/Tool (\w+) completed/)
  if (completeMatch && completeMatch[1]) {
    const nameMap: Record<string, string> = {
      maps_weather: '天气查询',
      maps_text_search: '地点搜索',
      maps_around_search: '周边搜索',
      maps_search_detail: '地点详情',
      maps_direction_driving: '驾车路线',
      maps_direction_walking: '步行路线',
      maps_direction_bicycling: '骑行路线',
      maps_direction_transit_integrated: '公交路线',
      maps_distance: '距离测量',
      maps_geo: '坐标解析',
      maps_regeocode: '地址解析',
      maps_schema_personal_map: '地图展示',
      maps_schema_navi: '导航链接',
      maps_schema_take_taxi: '打车链接',
      search_destination_info: '目的地信息',
    }
    const name = nameMap[completeMatch[1]]
    if (name) return name.replace('正在', '已完成').replace('...', '。')
  }
  return msg.slice(0, 50) + (msg.length > 50 ? '...' : '')
}

// ===== 本地 UI 辅助 =====
const expandedItems = ref<Set<string>>(new Set())

const typeIconMap: Record<string, string> = {
  '景点': '📍',
  '餐饮': '🍜',
  '交通': '🚗',
  '住宿': '🏨',
  '休闲': '🎭',
  '购物': '🛍️',
}

function isItemExpanded(dayNum: number, idx: number): boolean {
  return expandedItems.value.has(`${dayNum}-${idx}`)
}

function toggleItemExpand(dayNum: number, idx: number) {
  const k = `${dayNum}-${idx}`
  const s = new Set(expandedItems.value)
  if (s.has(k)) {
    s.delete(k)
  } else {
    s.add(k)
  }
  expandedItems.value = s
}

function hasValidCoords(loc: { lat: number; lng: number }): boolean {
  return !!(loc && loc.lat !== 0 && loc.lng !== 0)
}

function getTypeClass(t: string): string {
  const map: Record<string, string> = {
    '景点': 'spot',
    '餐饮': 'food',
    '交通': 'traffic',
    '住宿': 'hotel',
    '休闲': 'relax',
    '购物': 'shopping',
  }
  return map[t] || 'default'
}

// ===== 删除行程项（带二次确认） =====
async function handleDeleteItem(dayNum: number, idx: number, itemName?: string) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${itemName || '未命名'}」吗？`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    deleteItem(dayNum, idx)
    ElMessage.success('已删除')
  } catch {
    // 用户取消，不做任何操作
  }
}

// ===== 保存（包装 composable 以添加 UI 反馈） =====
async function handleSave() {
  try {
    await saveFromWorkspace()
    ElMessage.success('行程已保存！')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

// ===== 生命周期 =====
onMounted(async () => {
  const queryConvId = route.query.convId ? String(route.query.convId) : null
  const queryItineraryId = route.query.itineraryId ? String(route.query.itineraryId) : null

  console.log('[Workspace] onMounted - convId:', queryConvId, 'itineraryId:', queryItineraryId)

  // 优先从 convId 加载：恢复对话历史 + 关联行程
  if (queryConvId) {
    convId.value = queryConvId
    const { itineraryId: linkedItineraryId } = await loadConversationHistory(queryConvId)

    console.log('[Workspace] loaded history - linkedItineraryId:', linkedItineraryId)

    // 确定要加载的行程 ID：URL 参数优先，其次是对话关联的行程
    const effectiveItineraryId = queryItineraryId || linkedItineraryId

    if (effectiveItineraryId) {
      // 有保存的行程，从服务器加载
      console.log('[Workspace] loading itinerary from server:', effectiveItineraryId)
      await loadDetailForEdit(effectiveItineraryId)
    }
  } else if (queryItineraryId) {
    await loadDetailForEdit(queryItineraryId)
  }

  // 如果已有行程或有历史对话，自动切换到对话模式
  if (editablePlan.value || messages.value.length > 0) {
    console.log('[Workspace] switching to chat mode, editablePlan:', !!editablePlan.value, 'messages:', messages.value.length)
    inputMode.value = 'chat'
  }
})

// 退出工作台页面时自动保存行程并关联到对话
// 使用 onBeforeRouteLeave 处理 Vue Router 导航（点击返回按钮等）
onBeforeRouteLeave(async (to, from, next) => {
  console.log('[Workspace] onBeforeRouteLeave triggered - navigating from', from.path, 'to', to.path)
  console.log('[Workspace] current state - editablePlan:', !!editablePlan.value, 'convId:', convId.value)
  
  try {
    await autoSaveAndLinkItinerary()
  } catch (e) {
    console.warn('[Workspace] save before route leave failed:', e)
  }
  next()
})
</script>

<style scoped>
/* ====== 整体布局 ====== */
.workspace {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  overflow: hidden;
  background: #F8FBFF;
}

.ws-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  flex-shrink: 0;
  z-index: 10;
}

.ws-back-btn {
  padding: 8px 16px;
  border-radius: 10px;
  border: 1px solid rgba(51, 133, 255, 0.15);
  background: #fff;
  color: #3385FF;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-back-btn:hover { background: #F0F7FF; }

.ws-header-title {
  font-size: 18px;
  font-weight: 700;
  color: #1E3A5F;
  margin: 0;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.ws-action-btn {
  padding: 7px 16px;
  border-radius: 9px;
  border: 1px solid rgba(51, 133, 255, 0.15);
  background: #fff;
  color: #3385FF;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.ws-action-btn:hover:not(:disabled) {
  background: #F0F7FF;
  border-color: #3385FF;
}
.ws-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ====== 主体 ====== */
.ws-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

/* ====== 左侧对话区 ====== */
.ws-conversation {
  flex: 1;
  min-width: 400px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(51, 133, 255, 0.06);
  background: #F8FBFF;
}

.ws-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 欢迎卡片 */
.ws-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px 20px;
  text-align: center;
  animation: fadeInUp 0.4s ease;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

.ws-welcome-icon {
  font-size: 40px;
  margin-bottom: 4px;
}

.ws-welcome h3 {
  font-size: 20px;
  font-weight: 800;
  color: #1E3A5F;
  margin: 0;
}

.ws-welcome p {
  font-size: 13px;
  color: #6B8CBE;
  line-height: 1.5;
  margin: 0;
}

.ws-suggested-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 8px;
}

.ws-sp-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 12px;
  border: 1px solid rgba(51, 133, 255, 0.12);
  background: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 13px;
  color: #1E3A5F;
}
.ws-sp-card:hover {
  border-color: #3385FF;
  box-shadow: 0 2px 10px rgba(51, 133, 255, 0.1);
  transform: translateY(-1px);
}
.ws-sp-icon { font-size: 18px; }
.ws-sp-text { font-weight: 500; }

/* 消息行 */
.ws-msg {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  animation: fadeInUp 0.25s ease;
}
.ws-msg.user { flex-direction: row-reverse; }

.ws-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  flex-shrink: 0;
}
.user-avatar { background: linear-gradient(135deg, #3385FF, #1A6FFF); color: #fff; }
.assistant-avatar { background: linear-gradient(135deg, #FF9500, #FF6B00); color: #fff; }

.ws-bubble-wrapper {
  max-width: 88%;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.ws-msg.user .ws-bubble-wrapper { align-items: flex-end; }
.ws-msg.assistant .ws-bubble-wrapper { align-items: flex-start; }

.ws-bubble {
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.ws-bubble.user {
  background: linear-gradient(135deg, #3385FF, #1A6FFF);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.ws-bubble.assistant {
  background: #fff;
  color: #1E3A5F;
  border: 1px solid rgba(51, 133, 255, 0.08);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.ws-bubble-text {
  white-space: pre-wrap;
  line-height: 1.7;
}
.ws-bubble-text :deep(.md-h1) {
  font-size: 18px;
  font-weight: 700;
  color: #1E3A5F;
  margin: 12px 0 8px;
  padding-bottom: 6px;
  border-bottom: 2px solid rgba(51, 133, 255, 0.15);
}
.ws-bubble-text :deep(.md-h2) {
  font-size: 16px;
  font-weight: 700;
  color: #1E3A5F;
  margin: 10px 0 6px;
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.1);
}
.ws-bubble-text :deep(.md-h3) {
  font-size: 14px;
  font-weight: 600;
  color: #1E3A5F;
  margin: 8px 0 4px;
}
.ws-bubble-text :deep(.md-code-block) {
  background: #F1F5F9;
  border: 1px solid rgba(51, 133, 255, 0.1);
  border-radius: 8px;
  padding: 12px;
  margin: 8px 0;
  overflow-x: auto;
  font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #334155;
}
.ws-bubble-text :deep(.md-code-block code) {
  background: transparent;
  padding: 0;
  border-radius: 0;
  font-size: inherit;
  color: inherit;
}
.ws-bubble-text :deep(.md-inline-code) {
  background: rgba(51, 133, 255, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #3385FF;
  font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
}
.ws-bubble-text :deep(strong) { font-weight: 700; }
.ws-bubble-text :deep(em) { font-style: italic; color: #64748B; }
.ws-bubble-text :deep(.md-ul),
.ws-bubble-text :deep(.md-ol) {
  margin: 8px 0;
  padding-left: 24px;
}
.ws-bubble-text :deep(.md-li),
.ws-bubble-text :deep(.md-oli) {
  margin: 4px 0;
  line-height: 1.6;
}
.ws-bubble-text :deep(.md-li::marker) {
  color: #3385FF;
}
.ws-bubble-text :deep(.md-oli::marker) {
  color: #3385FF;
  font-weight: 600;
}
.ws-bubble-text :deep(.md-hr) {
  border: none;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(51, 133, 255, 0.2), transparent);
  margin: 12px 0;
}
.ws-bubble-text :deep(.md-link) {
  color: #3385FF;
  text-decoration: none;
  font-weight: 500;
  border-bottom: 1px solid rgba(51, 133, 255, 0.3);
  transition: all 0.15s;
}
.ws-bubble-text :deep(.md-link:hover) {
  color: #1A6FFF;
  border-bottom-color: #1A6FFF;
}

.ws-time {
  font-size: 11px;
  color: #B0C8DE;
  padding: 0 4px;
}



/* ====== 流式生成指示器 ====== */
.ws-streaming {
  margin-top: 10px;
  padding: 12px 14px;
  background: linear-gradient(135deg, #F8FAFD 0%, #F0F4F8 100%);
  border: 1px solid rgba(51, 133, 255, 0.08);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ws-streaming-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #5B7FA5;
}

.ws-streaming-dots {
  display: inline-flex;
  gap: 3px;
}
.ws-streaming-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #3385FF;
  animation: dotBounce 1.2s infinite ease-in-out both;
}
.ws-streaming-dots span:nth-child(1) { animation-delay: -0.32s; }
.ws-streaming-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* 迷你阶段进度条 */
.ws-mini-progress {
  display: flex;
  align-items: center;
  gap: 4px;
}
.ws-phase {
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #94A3B8;
  background: #F1F5F9;
  transition: all 0.3s;
}
.ws-phase.active {
  color: #fff;
  background: linear-gradient(135deg, #3385FF, #1A6FFF);
  box-shadow: 0 2px 6px rgba(51, 133, 255, 0.25);
}
.ws-phase-sep {
  width: 12px;
  height: 2px;
  background: #E2E8F0;
  border-radius: 1px;
}

/* 实时日志 */
.ws-live-logs {
  font-size: 12px;
}
.ws-live-logs summary {
  cursor: pointer;
  color: #7A8FA6;
  font-weight: 500;
  padding: 2px 0;
  user-select: none;
}
.ws-live-logs summary:hover { color: #5B7FA5; }

.ws-log-list {
  max-height: 180px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 6px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
}

.ws-log-entry {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 3px 6px;
  border-radius: 4px;
  font-family: 'SF Mono', 'Consolas', monospace;
  font-size: 11px;
  line-height: 1.5;
  color: #5B7FA5;
}
.ws-log-entry.phase { color: #6366f1; }
.ws-log-entry.thought { color: #d97706; }
.ws-log-entry.tool { color: #3385FF; }
.ws-log-entry.error { color: #EF4444; }
.ws-log-icon { flex-shrink: 0; }
.ws-log-text { word-break: break-all; }

/* ====== 输入区域 ====== */
.ws-input-area {
  padding: 12px 16px;
  border-top: 1px solid rgba(51, 133, 255, 0.08);
  background: linear-gradient(180deg, #FFFFFF 0%, #FAFCFF 100%);
  flex-shrink: 0;
}

/* ====== 模式切换标签 ====== */
.ws-mode-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 12px;
  background: #EEF1F6;
  border-radius: 12px;
  padding: 3px;
}

.ws-mode-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #7A8FA6;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ws-mode-tab.active {
  background: #fff;
  color: #1A6FFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.07);
  font-weight: 600;
}
.ws-mode-tab:hover:not(.active) { color: #5B7FA5; }
.ws-mode-icon { font-size: 16px; }



/* ====== 右侧画布 ====== */
.ws-canvas {
  width: 540px;
  min-width: 480px;
  flex-shrink: 0;
  background: #fff;
  border-left: 1px solid rgba(51, 133, 255, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.3s ease;
}

.ws-canvas-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.06);
  flex-shrink: 0;
  background: #fafbff;
}
.ws-canvas-header h3 {
  font-size: 14px;
  font-weight: 700;
  color: #1E3A5F;
  margin: 0;
}

.ws-canvas-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ws-canvas-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px 16px;
  text-align: center;
  flex: 1;
}
.ws-canvas-empty-icon { font-size: 40px; }
.ws-canvas-empty p { font-size: 13px; color: #94A3B8; margin: 0; }

/* 行程标题 */
.ws-plan-title {
  width: 100%;
  border: none;
  background: transparent;
  padding: 4px 6px;
  font-size: 20px;
  font-weight: 800;
  color: #1E3A5F;
  outline: none;
  border-radius: 6px;
  transition: background 0.15s;
}
.ws-plan-title::placeholder { color: #B8C9E0; font-weight: 600; }
.ws-plan-title:focus { background: rgba(51, 133, 255, 0.04); box-shadow: 0 0 0 3px rgba(51, 133, 255, 0.08); }

/* 元信息 */
.ws-plan-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 8px 10px;
  background: #F8FAFD;
  border-radius: 10px;
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

/* 天数区块 */
.ws-day-section { margin-bottom: 4px; }

.ws-day-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0 6px;
}
.ws-day-num {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3385FF, #1A6FFF);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(51, 133, 255, 0.25);
}
.ws-day-label {
  font-size: 14px;
  font-weight: 700;
  color: #1E3A5F;
  flex: 1;
}
.ws-add-item {
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px dashed rgba(51, 133, 255, 0.25);
  background: transparent;
  color: #3385FF;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-add-item:hover { background: #F0F7FF; border-color: #3385FF; border-style: solid; }

.ws-items {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-left: 4px;
}

.ws-item {
  border-radius: 9px;
  background: #FAFBFC;
  border: 1px solid rgba(51, 133, 255, 0.06);
  transition: all 0.15s;
  overflow: hidden;
}
.ws-item:hover {
  border-color: rgba(51, 133, 255, 0.15);
  box-shadow: 0 1px 4px rgba(51, 133, 255, 0.06);
  background: #fff;
}
.ws-item.expanded {
  border-color: rgba(51, 133, 255, 0.2);
  box-shadow: 0 2px 8px rgba(51, 133, 255, 0.08);
  background: #fff;
}

.ws-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.1s;
}
.ws-item-header:hover {
  background: rgba(51, 133, 255, 0.02);
}

.ws-item-icon { font-size: 16px; flex-shrink: 0; }
.ws-item-name {
  font-size: 13px;
  font-weight: 600;
  color: #1E3A5F;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ws-item-expand-arrow {
  font-size: 9px;
  color: #94A3B8;
  flex-shrink: 0;
  transition: transform 0.2s;
}

.ws-item-actions {
  display: flex;
  gap: 4px;
}

.ws-item-btn {
  padding: 3px 8px;
  border-radius: 5px;
  border: none;
  background: transparent;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.1s;
}
.ws-item-btn.delete { color: #94A3B8; }
.ws-item-btn.delete:hover { color: #EF4444; background: rgba(239, 68, 68, 0.06); }

/* 行程项展开详情 */
.ws-item-detail {
  padding: 0 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid rgba(51, 133, 255, 0.06);
  background: rgba(248, 251, 255, 0.6);
  cursor: default;
}

.ws-item-field {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.ws-item-field.half { flex: 1; }

.ws-item-label {
  font-size: 11px;
  font-weight: 600;
  color: #7A8FA6;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.ws-item-input,
.ws-item-select {
  padding: 6px 10px;
  border: 1px solid rgba(51, 133, 255, 0.12);
  border-radius: 6px;
  font-size: 13px;
  color: #1E3A5F;
  background: #fff;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.ws-item-input:focus,
.ws-item-select:focus,
.ws-item-textarea:focus {
  border-color: #3385FF;
  box-shadow: 0 0 0 3px rgba(51, 133, 255, 0.08);
}
.ws-item-input::placeholder,
.ws-item-textarea::placeholder { color: #B8C9E0; }

.ws-item-textarea {
  padding: 6px 10px;
  border: 1px solid rgba(51, 133, 255, 0.12);
  border-radius: 6px;
  font-size: 13px;
  color: #1E3A5F;
  background: #fff;
  outline: none;
  resize: vertical;
  min-height: 40px;
  font-family: inherit;
  line-height: 1.5;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.ws-item-location-row {
  display: flex;
  gap: 8px;
}

.ws-item-map-row {
  margin-top: 2px;
}

.ws-item-map-btn {
  width: 100%;
  padding: 7px 12px;
  border: 1px dashed rgba(51, 133, 255, 0.2);
  border-radius: 7px;
  background: transparent;
  color: #3385FF;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-item-map-btn:hover:not(.disabled) {
  background: rgba(51, 133, 255, 0.04);
  border-style: solid;
}
.ws-item-map-btn.disabled {
  color: #B8C9E0;
  cursor: not-allowed;
}

/* 展开/折叠动画 */
.item-expand-enter-active,
.item-expand-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}
.item-expand-enter-from,
.item-expand-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.ws-empty-day {
  font-size: 12px;
  color: #94A3B8;
  text-align: center;
  padding: 14px;
  border: 1.5px dashed rgba(51, 133, 255, 0.1);
  border-radius: 9px;
  background: #fafcff;
}

/* 保存按钮 */
.ws-save-btn {
  width: 100%;
  padding: 12px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 3px 10px rgba(51, 133, 255, 0.25);
  margin-top: 8px;
}
.ws-save-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 5px 16px rgba(51, 133, 255, 0.35);
}
.ws-save-btn:disabled { background: #93c5fd; cursor: not-allowed; box-shadow: none; }

/* TransitionGroup 动画 */
.item-list-enter-active { transition: all 0.3s ease; }
.item-list-leave-active { transition: all 0.2s ease; position: absolute; }
.item-list-enter-from { opacity: 0; transform: translateX(-12px); }
.item-list-leave-to { opacity: 0; transform: translateX(12px); }
.item-list-move { transition: transform 0.3s ease; }
</style>
