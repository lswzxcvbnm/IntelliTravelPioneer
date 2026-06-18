<template>
  <div class="workspace">
    <!-- 顶部导航栏 -->
    <header class="ws-header">
      <BackButton class="ws-back-btn">← 返回</BackButton>
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
                @click="handleSuggestedPrompt(sp)"
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
                <div class="ws-msg-meta">
                  <span class="ws-time">{{ formatTime(msg.time) }}</span>
                  <span class="ws-msg-status sent">✓ 已发送</span>
                </div>
              </div>
            </template>

            <!-- AI 助手消息 -->
            <template v-if="msg.role === 'assistant'">
              <div class="ws-avatar assistant-avatar">🤖</div>
              <div class="ws-bubble-wrapper">
                <div class="ws-bubble assistant">
                  <!-- 文本内容（支持展开/收起） -->
                  <div v-if="msg.content && (!isLongContent(msg.content) || isMessageExpanded(msg.id))" class="ws-bubble-text" v-html="formatContent(msg.content)"></div>
                  <div v-else-if="msg.content && isLongContent(msg.content)" class="ws-bubble-text" v-html="formatContent(getTruncatedContent(msg.content))"></div>
                  <button v-if="isLongContent(msg.content)" class="ws-expand-btn" @click="toggleMessageExpand(msg.id)">
                    {{ isMessageExpanded(msg.id) ? '收起' : '展开全部' }}
                  </button>

                  <!-- 内联工具调用卡片 -->
                  <ToolCallCard
                    v-if="msg.toolCalls && parseToolType(msg.toolCalls) === 'tools'"
                    :toolCalls="msg.toolCalls"
                  />

                  <!-- 思考过程（生成完成后显示） -->
                  <div
                    v-if="!msg.streaming && msg.thinkingLogs && msg.thinkingLogs.length > 0"
                    class="ws-thinking"
                  >
                    <button
                      class="ws-thinking-toggle"
                      @click="toggleThinkingExpand(msg.id)"
                    >
                      <span class="ws-thinking-icon">🧠</span>
                      <span class="ws-thinking-label">思考过程</span>
                      <span class="ws-thinking-count">{{ msg.thinkingLogs.length }} 步</span>
                      <span class="ws-thinking-arrow" :class="{ expanded: isThinkingExpanded(msg.id) }">▸</span>
                    </button>
                    <div v-if="isThinkingExpanded(msg.id)" class="ws-thinking-content">
                      <div
                        v-for="(log, li) in msg.thinkingLogs"
                        :key="li"
                        class="ws-thinking-entry"
                        :class="log.type"
                      >
                        <span class="ws-thinking-entry-icon">{{ log.icon }}</span>
                        <span class="ws-thinking-entry-text">{{ log.text }}</span>
                      </div>
                    </div>
                  </div>

                  <!-- 流式生成指示器 -->
                  <div v-if="msg.streaming" class="ws-streaming">
                    <div class="ws-streaming-header">
                      <span class="ws-streaming-dots">
                        <span></span><span></span><span></span>
                      </span>
                      <span>{{ currentAction || 'AI 正在为你规划行程...' }}</span>
                    </div>

                    <!-- 阶段进度条 -->
                    <div v-if="isGenerating || isRefining" class="ws-mini-progress">
                      <div class="ws-phase" :class="{ active: currentPhase.includes('PLAN') }">规划</div>
                      <div class="ws-phase-sep"></div>
                      <div class="ws-phase" :class="{ active: currentPhase.includes('EXECUTE') }">执行</div>
                      <div class="ws-phase-sep"></div>
                      <div class="ws-phase" :class="{ active: currentPhase.includes('VERIFY') }">验证</div>
                    </div>

                    <!-- 实时日志（默认折叠，不干扰对话体验） -->
                    <details v-if="liveLogs.length > 0" class="ws-live-logs">
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
              :disabled="isGenerating || isRefining"
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
            :skills="mySkills"
            :prefill="formPrefill"
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
      <ItineraryCanvas
        :editablePlan="editablePlan"
        :isSaving="isSaving"
        :totalItems="totalItems"
        :expandedItems="expandedItems"
        @save="handleSave"
        @item-toggle="toggleItemExpand"
        @item-delete="handleDeleteItem"
        @drag-start="onDragStart"
        @item-drop="(e, day, idx) => onDrop(e, day, idx)"
        @day-drop="onDayDrop"
      />


    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateItineraryStream, modifyItineraryStream, saveItinerary, updateItinerary } from '../../api/itinerary'
import type { ItineraryRequest, ItineraryPlan, GenerationStats } from '../../api/itinerary'
import { linkItinerary, updateConversation } from '../../api/conversation'
import { listMySkills, type SkillVO } from '../../api/skill'
import { useConversation, type ThinkingLog } from '../../composables/useConversation'
import { useItinerary } from '../../composables/useItinerary'
import { useDragDrop } from '../../composables/useDragDrop'
import BackButton from '../../components/BackButton.vue'
import { extractActionFromLog, TYPE_ICON_MAP } from '../../constants/tool'
import ToolCallCard from './ToolCallCard.vue'
import ChatInput from './ChatInput.vue'
import FormInput from './FormInput.vue'
import ItineraryCanvas from './ItineraryCanvas.vue'

const router = useRouter()
const route = useRoute()

// ===== 初始化 composables =====
const {
  messages, messagesRef, addUserMessage, addAssistantMessage,
  updateMessage, finalizeMessage, scrollToBottom,
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
  '成都4天美食之旅，吃遍地道小吃',
  '西安3天古都文化探索',
  '厦门2天海岛休闲度假',
]
const spIcons = ['🏛', '💼', '😂', '🍜', '🏯', '🏖️']

// ===== 消息展开状态 =====
const expandedMessages = ref<Set<string>>(new Set())
const expandedThinking = ref<Set<string>>(new Set())

function isMessageExpanded(msgId: string): boolean {
  return expandedMessages.value.has(msgId)
}

function toggleMessageExpand(msgId: string) {
  const s = new Set(expandedMessages.value)
  if (s.has(msgId)) {
    s.delete(msgId)
  } else {
    s.add(msgId)
  }
  expandedMessages.value = s
}

function isThinkingExpanded(msgId: string): boolean {
  return expandedThinking.value.has(msgId)
}

function toggleThinkingExpand(msgId: string) {
  const s = new Set(expandedThinking.value)
  if (s.has(msgId)) {
    s.delete(msgId)
  } else {
    s.add(msgId)
  }
  expandedThinking.value = s
}

function isLongContent(content: string): boolean {
  return !!content && content.length > 500
}

function getTruncatedContent(content: string): string {
  if (!content || content.length <= 500) return content
  return content.slice(0, 500) + '...'
}

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

// 当前正在生成的消息ID及其思考日志
const currentMsgId = ref<string | null>(null)
const currentThinkingLogs = ref<ThinkingLog[]>([])

// ===== 技能状态 =====
const mySkills = ref<SkillVO[]>([])

// ===== 输入模式（表单 / 对话） =====
// 如果是延续对话（有 convId），默认使用对话模式
const inputMode = ref<'form' | 'chat'>(route.query.convId ? 'chat' : 'form')

// 表单预填（从建议提示词点击进入时使用）
const formPrefill = ref<{ destination: string; days?: number } | null>(null)

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

/** 根据行程计划生成自然语言的对话回复 */
function buildPlanSummary(plan: ItineraryPlan): string {
  const dest = plan.destination || ''
  const totalSpots = plan.dailyPlans.reduce((sum, d) => sum + d.items.length, 0)
  const lines: string[] = []
  
  lines.push(`好的！我已为你规划了${dest ? `**${dest}**` : ''}**${plan.totalDays} 天**行程：`)
  lines.push('')
  
  plan.dailyPlans.forEach(day => {
    const items = day.items.slice(0, 3).map(it => it.name || '未命名').join(' → ')
    const more = day.items.length > 3 ? ` 等 ${day.items.length} 项` : ''
    lines.push(`📅 **Day ${day.dayNumber}**（${day.items.length} 项）`)
    lines.push(`　${items}${more}`)
  })
  
  lines.push('')
  lines.push(`预算约 ¥${plan.estimatedCost || 0}，共 ${totalSpots} 个地点。行程已展示在右侧画布中，你可以自由调整或继续与我对话修改。`)
  
  return lines.join('\n')
}

// ===== 自动更新对话标题和标签 =====
const hasAutoUpdatedTitle = ref(false)

async function autoUpdateConversationMeta(userText: string) {
  if (!convId.value || hasAutoUpdatedTitle.value) return
  const destination = extractDestination(userText)
  if (!destination) return

  hasAutoUpdatedTitle.value = true
  try {
    const days = extractDays(userText)
    const title = days ? `${destination}${days}天游` : `${destination}之旅`
    const tags = destination
    await updateConversation(convId.value, { title, tags })
  } catch (e) {
    console.warn('[Workspace] auto update conversation title failed:', e)
  }
}

// ===== 建议提示词点击 =====
function handleSuggestedPrompt(text: string) {
  if (isGenerating.value || isRefining.value) return

  if (inputMode.value === 'form') {
    // 表单模式：解析目的地和天数，预填收起表单并直接生成
    const dest = extractDestination(text) || '未指定'
    const days = extractDays(text) ?? 3
    formPrefill.value = { destination: dest, days }
    handleFormSubmit({
      destination: dest,
      days,
      budgetLevel: 'medium',
      interests: extractInterests(text),
      additionalNotes: text,
    })
  } else {
    // 对话模式：直接发送
    handleSend(text)
  }
}

// ===== 核心发送逻辑 =====
async function handleSend(text: string) {
  if (!text || isGenerating.value || isRefining.value) return

  addUserMessage(text)
  const assistMsgId = addAssistantMessage()
  await scrollToBottom()

  // 首次发送时自动更新对话标题和标签
  autoUpdateConversationMeta(text)

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
  skillIds?: string[]
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

  // 表单模式下直接使用目的地信息更新对话标题和标签
  if (convId.value && !hasAutoUpdatedTitle.value) {
    hasAutoUpdatedTitle.value = true
    const title = `${request.destination}${request.days}天游`
    const tags = request.destination
    updateConversation(convId.value, { title, tags }).catch(e =>
      console.warn('[Workspace] auto update conversation title failed:', e)
    )
  }

  isGenerating.value = true
  liveLogs.value = []
  currentPhase.value = ''
  currentMsgId.value = assistMsgId
  currentThinkingLogs.value = []

  try {
    await generateItineraryStream(
      request,
      (phase) => {
        currentPhase.value = phase.type
        addLog('phase', '📋', phase.message)
      },
      (msg) => {
        // 思考过程只记录到日志，不显示在对话中
        addLog('thought', '💡', msg.slice(0, 120))
        currentAction.value = extractActionFromLog(msg) || '正在思考...'
      },
      (msg) => {
        addLog('tool', '🔧', msg)
        currentAction.value = extractActionFromLog(msg)
      },
      (plan) => {
        editablePlan.value = normalizePlan(plan)
        // 生成自然语言总结替换对话内容
        updateMessage(assistMsgId, { content: buildPlanSummary(plan), streaming: false })
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
    // 生成完成后，将思考日志附加到消息上
    updateMessage(assistMsgId, { thinkingLogs: [...currentThinkingLogs.value] })
    finalizeMessage(assistMsgId)
  } catch (e: any) {
    updateMessage(assistMsgId, { content: `生成失败: ${e?.message || '未知错误'}`, streaming: false })
  } finally {
    isGenerating.value = false
    inputMode.value = 'chat'
    currentMsgId.value = null
    currentThinkingLogs.value = []
  }
}

// ===== SSE 流式生成 =====
async function runGenerate(msgId: string, userPrompt: string) {
  isGenerating.value = true
  liveLogs.value = []
  currentPhase.value = ''
  currentMsgId.value = msgId
  currentThinkingLogs.value = []

  try {
    const request = parseUserRequest(userPrompt)

    await generateItineraryStream(
      request,
      (phase) => {
        currentPhase.value = phase.type
        addLog('phase', '📋', phase.message)
      },
      (msg) => {
        // 思考过程只记录到日志，不显示在对话中
        addLog('thought', '💡', msg.slice(0, 120))
        currentAction.value = extractActionFromLog(msg) || '正在思考...'
      },
      (msg) => {
        addLog('tool', '🔧', msg)
        currentAction.value = extractActionFromLog(msg)
      },
      (plan) => {
        editablePlan.value = normalizePlan(plan)
        // 生成自然语言总结替换对话内容
        updateMessage(msgId, { content: buildPlanSummary(plan), streaming: false })
      },
      (error) => {
        updateMessage(msgId, { content: `❌ 生成失败: ${error.message || '未知错误'}`, streaming: false })
      },
      (stats) => {
        activeStats.value = stats
        if (stats.toolCalls?.length) {
          updateMessage(msgId, { toolCalls: JSON.stringify(stats.toolCalls) })
        }
      },
      (cid) => { convId.value = cid }
    )

    // 生成完成后，将思考日志附加到消息上
    updateMessage(msgId, { thinkingLogs: [...currentThinkingLogs.value] })
    finalizeMessage(msgId)
  } catch (e: any) {
    updateMessage(msgId, { content: `❌ 生成失败: ${e?.message || '未知错误'}`, streaming: false })
  } finally {
    isGenerating.value = false
    currentMsgId.value = null
    currentThinkingLogs.value = []
  }
}

async function runRefine(msgId: string, userPrompt: string) {
  isRefining.value = true
  liveLogs.value = []
  currentMsgId.value = msgId
  currentThinkingLogs.value = []

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
        // 思考过程只记录到日志，不显示在对话中
        addLog('thought', '💡', msg.slice(0, 120))
        currentAction.value = extractActionFromLog(msg) || '正在思考...'
      },
      (msg) => {
        addLog('tool', '🔧', msg)
        currentAction.value = extractActionFromLog(msg)
      },
      (plan) => {
        editablePlan.value = mergeRefinedPlan(originalPlan, normalizePlan(plan))
        // 优化完成后生成自然语言回复
        const summary = `好的！已根据你的要求优化了行程。修改后的方案共 **${editablePlan.value!.totalDays} 天**、${editablePlan.value!.dailyPlans.reduce((s, d) => s + d.items.length, 0)} 个地点。查看右侧画布即可看到更新后的行程。`
        updateMessage(msgId, { content: summary, streaming: false })
      },
      (error) => {
        updateMessage(msgId, { content: `❌ 优化失败: ${error.message || '未知错误'}`, streaming: false })
      },
      (stats) => {
        activeStats.value = stats
        if (stats.toolCalls?.length) {
          updateMessage(msgId, { toolCalls: JSON.stringify(stats.toolCalls) })
        }
      },
      (cid) => { convId.value = cid }
    )

    // 优化完成后，将思考日志附加到消息上
    updateMessage(msgId, { thinkingLogs: [...currentThinkingLogs.value] })
    finalizeMessage(msgId)
  } catch (e: any) {
    updateMessage(msgId, { content: `❌ 优化失败: ${e?.message || '未知错误'}`, streaming: false })
  } finally {
    isRefining.value = false
    currentMsgId.value = null
    currentThinkingLogs.value = []
  }
}

// ===== 辅助方法 =====
function addLog(type: string, icon: string, text: string) {
  const log = { type, icon, text: text.slice(0, 200) }
  liveLogs.value.push(log)
  // 同时收集到当前消息的思考日志中
  currentThinkingLogs.value.push(log as ThinkingLog)
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



// ===== 本地 UI 辅助 =====
const expandedItems = ref<Set<string>>(new Set())

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

// ===== 获取技能列表 =====
async function fetchMySkills() {
  try {
    const r = await listMySkills()
    mySkills.value = (r as any).data || []
  } catch { /* ignore */ }
}

// ===== 生命周期 =====
onMounted(async () => {
  const queryConvId = route.query.convId ? String(route.query.convId) : null
  const queryItineraryId = route.query.itineraryId ? String(route.query.itineraryId) : null

  console.log('[Workspace] onMounted - convId:', queryConvId, 'itineraryId:', queryItineraryId)

  // 优先从 convId 加载：恢复对话历史 + 关联行程
  if (queryConvId) {
    convId.value = queryConvId
    const { itineraryId: linkedItineraryId, title: convTitle } = await loadConversationHistory(queryConvId)

    console.log('[Workspace] loaded history - linkedItineraryId:', linkedItineraryId, 'title:', convTitle)

    // 如果已有非默认标题，标记为已自动更新，避免后续重复更新
    if (convTitle && convTitle !== '新对话') {
      hasAutoUpdatedTitle.value = true
    }

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

  // 获取技能列表
  fetchMySkills()
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
  outline: none;
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
  outline: none;
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
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
  margin-top: 12px;
  width: 100%;
  max-width: 600px;
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
  outline: none;
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

/* ====== 思考过程折叠框 ====== */
.ws-thinking {
  margin-top: 12px;
  border-radius: 10px;
  border: 1px solid rgba(99, 102, 241, 0.12);
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.03) 0%, rgba(139, 92, 246, 0.03) 100%);
  overflow: hidden;
}

.ws-thinking-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px 14px;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background 0.2s;
  outline: none;
}
.ws-thinking-toggle:hover {
  background: rgba(99, 102, 241, 0.05);
}

.ws-thinking-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.ws-thinking-label {
  font-size: 13px;
  font-weight: 600;
  color: #6366f1;
  flex: 1;
  text-align: left;
}

.ws-thinking-count {
  font-size: 11px;
  color: #94a3b8;
  background: rgba(99, 102, 241, 0.08);
  padding: 2px 8px;
  border-radius: 10px;
  flex-shrink: 0;
}

.ws-thinking-arrow {
  font-size: 12px;
  color: #94a3b8;
  transition: transform 0.2s ease;
  flex-shrink: 0;
}
.ws-thinking-arrow.expanded {
  transform: rotate(90deg);
}

.ws-thinking-content {
  max-height: 300px;
  overflow-y: auto;
  padding: 0 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  animation: fadeInUp 0.2s ease;
}

.ws-thinking-entry {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
  background: rgba(255, 255, 255, 0.6);
}

.ws-thinking-entry.phase {
  color: #6366f1;
  background: rgba(99, 102, 241, 0.06);
}
.ws-thinking-entry.thought {
  color: #d97706;
  background: rgba(217, 119, 6, 0.06);
}
.ws-thinking-entry.tool {
  color: #3385FF;
  background: rgba(51, 133, 255, 0.06);
}
.ws-thinking-entry.error {
  color: #EF4444;
  background: rgba(239, 68, 68, 0.06);
}

.ws-thinking-entry-icon {
  flex-shrink: 0;
  font-size: 12px;
  line-height: 1.5;
}

.ws-thinking-entry-text {
  flex: 1;
  min-width: 0;
  word-break: break-word;
  font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
}

/* ====== 输入区域（悬浮容器） ====== */
.ws-input-area {
  padding: 12px 20px 16px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ws-input-area > * {
  width: 100%;
  max-width: 680px;
}

/* ====== 模式切换标签 ====== */
.ws-mode-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 10px;
  background: #E8EDF5;
  border-radius: 10px;
  padding: 3px;
  max-width: 280px;
  align-self: center;
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
  outline: none;
}

.ws-mode-tab.active {
  background: #fff;
  color: #1A6FFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.07);
  font-weight: 600;
}
.ws-mode-tab:hover:not(.active):not(:disabled) { color: #5B7FA5; }
.ws-mode-tab:disabled { opacity: 0.4; cursor: not-allowed; }
.ws-mode-icon { font-size: 16px; }



</style>
