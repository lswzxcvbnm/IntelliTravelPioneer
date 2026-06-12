﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="customize-shell">
    <div class="customize-main" :class="{ 'with-panel': !agentPanelCollapsed }">
      <header class="top-bar">
        <button type="button" class="back-btn" @click="goBack">← 返回</button>
        <h1>{{ isEditMode ? '编辑行程计划' : '创建行程计划' }}</h1>
        <div class="top-bar-right">
          <button v-if="editablePlan" type="button"
            class="top-bar-toggle-form" :class="{ active: showForm }"
            @click="showForm = !showForm">需求</button>
          <button type="button"
            class="top-bar-toggle-panel" :class="{ active: !agentPanelCollapsed }"
            @click="agentPanelCollapsed = !agentPanelCollapsed">AI</button>
          <button type="button"
            class="top-bar-toggle-panel top-bar-chat-btn" :class="{ active: showChatPanel }"
            @click="toggleChatPanel">💬 对话</button>
        </div>
      </header>

      <div class="content-scroll" ref="contentScrollRef">
        <!-- Form Section - always rendered, collapsible when editablePlan exists -->
        <section class="card form-section" :class="{ 'form-collapsed': editablePlan && !showForm }">
          <!-- Collapsed header bar -->
          <div v-if="editablePlan && !showForm" class="form-section-header" @click="showForm = true">
            <div class="form-section-title">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              行程需求
            </div>
            <span>{{ request.destination || '未填写' }} · {{ request.days }}天 · {{ budgetLabel }}</span>
            <span class="form-section-arrow">▶</span>
          </div>
          <!-- Full form body -->
          <template v-if="!editablePlan || showForm">
            <div class="form-section-body">
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
                <div class="input-card full-width" v-if="mySkills.length > 0">
                  <span class="label-text">🧩 启用技能</span>
                  <div class="chips-container">
                    <button v-for="skill in mySkills" :key="skill.id" class="chip-pill chip-skill"
                      :class="{ active: request.skillIds?.includes(skill.id) }"
                      @click="toggleSkill(skill.id)">{{ skill.name }}</button>
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
                <button class="primary" @click="handleGenerate" :disabled="isLoadingGenerate">
                  {{ isLoadingGenerate ? '正在生成...' : '✨ 生成 AI 方案' }}
                </button>
              </div>
            </div>
          </template>
        </section>

        <!-- Editor Section - shown when editablePlan exists -->
        <section class="card editor-section" v-if="editablePlan">
          <div class="edit-plan-header">
            <div class="eph-left">
              <h2 class="eph-title-wrap">
                <input class="eph-title" v-model.trim="editablePlan.title" placeholder="给你的行程起个名字" />
              </h2>
              <div class="eph-meta">
                <span class="eph-tag">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                  {{ editablePlan.totalDays }} 天行程
                </span>
                <span class="eph-tag">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>
                  预算 ¥{{ formatCurrency(editablePlan.estimatedCost || 0).replace('￥', '') }}
                </span>
                <span class="eph-tag eph-tag-route">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="3 6 9 3 15 6 21 3 21 18 15 21 9 18 3 21"/><line x1="9" y1="3" x2="9" y2="18"/><line x1="15" y1="6" x2="15" y2="21"/></svg>
                  {{ routeItemCount }} 个路线项
                </span>
              </div>
            </div>
            <div class="eph-actions">
              <button v-if="activeConversationId || route.query.convId"
                class="eph-btn eph-conv-link"
                @click="router.push(`/conversation/${activeConversationId || route.query.convId}`)"
                title="查看 AI 对话历史与推理过程">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
                对话记录
              </button>
              <button class="primary eph-btn eph-save" @click="savePlan" :disabled="isSaving">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
                {{ isSaving ? '保存中...' : isEditMode ? '更新方案' : '保存方案' }}
              </button>
            </div>
          </div>

          <div class="ai-refine-box">
            <div class="ai-refine-header">
              <span class="ai-refine-badge">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a10 10 0 0110 10c0 5.52-4.48 10-10 10S2 17.52 2 12"/><path d="m12 12-.5-8"/></svg>
                AI 智能优化
              </span>
              <span class="ai-refine-hint">描述你的修改意图，AI 将自动调整行程</span>
            </div>
            <div class="ai-chat-input-row">
              <textarea v-model="refinePrompt" rows="1"
                placeholder="例如：把第一天的景点换成室内的、晚餐都安排在酒店附近、增加一个博物馆..."
                @keydown.enter.exact.prevent="handleAiRefineQuick" />
              <button class="primary mini ai-send-btn" @click="handleAiRefine" :disabled="isRefining || !refinePrompt.trim()">
                {{ isRefining ? '思考中...' : '发送' }}
              </button>
            </div>
            <div v-if="refineHistory.length > 0" class="refine-history">
              <div v-for="(rh, ri) in refineHistory.slice(-3)" :key="ri" class="refine-history-item">
                <span class="rh-user">{{ rh.prompt }}</span>
                <span class="rh-status" :class="rh.success ? 'ok' : 'fail'">{{ rh.success ? '✓' : '✗' }}</span>
              </div>
            </div>
          </div>

          <div class="timeline-view">
            <div class="day-section" v-for="(day, di) in editablePlan.dailyPlans" :key="day.dayNumber"
              @dragover.prevent @drop="onDayDrop(day.dayNumber)">
              <div class="day-header-card">
                <div class="day-header-left">
                  <div class="day-index-badge">{{ day.dayNumber }}</div>
                  <div class="day-header-info">
                    <span class="day-header-label">第 {{ day.dayNumber }} 天</span>
                    <span class="day-header-sub">{{ day.items.length }} 个地点 · {{ day.items.filter(i => i.type === '美食').length }} 餐饮 · {{ day.items.filter(i => i.type === '住宿').length }} 住宿</span>
                  </div>
                </div>
                <button class="btn-add-item" @click="addItem(day.dayNumber)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                  添加
                </button>
              </div>
              <div class="timeline-list">
                <div class="timeline-row" v-for="(item, idx) in day.items" :key="`${day.dayNumber}-${idx}`"
                  draggable="true" @dragstart="onDragStart(day.dayNumber, idx)"
                  @dragover.prevent @drop.stop="onDrop(day.dayNumber, idx)"
                  :class="`type-border-${getTypeClass(item.type)}`">
                  <div class="timeline-thread">
                    <div class="node-circle" :class="`bg-${getTypeClass(item.type)}`">{{ getTypeIcon(item.type) }}</div>
                  </div>
                  <div class="timeline-card-wrap">
                    <div class="timeline-card">
                      <div class="card-header-row">
                        <div class="card-info">
                          <div class="card-title-row">
                            <span class="card-type-tag" :class="`tag-${getTypeClass(item.type)}`">{{ item.type }}</span>
                            <div class="card-title">{{ item.name || '未命名路线项' }}</div>
                            <button v-if="hasLocation(item)" class="map-link-btn" @click="openMap(item)" title="在地图中查看">
                              📍 地图
                            </button>
                          </div>
                          <div class="card-desc">{{ item.description || '暂无描述' }}</div>
                          <div v-if="hasLocation(item) && item.location?.name" class="card-location">
                            <span class="location-icon">📍</span>
                            <span class="location-name">{{ item.location.name }}</span>
                            <span v-if="formatCoordinates(item)" class="location-coords">
                              {{ formatCoordinates(item) }}
                            </span>
                          </div>
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
                <div v-if="day.items.length === 0" class="empty-day-placeholder">
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
                  <span>点击上方「添加」按钮或拖拽来安排今日行程</span>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <AgentPanel
      v-if="!showChatPanel"
      :collapsed="agentPanelCollapsed"
      :is-generating="isAgentGenerating"
      :is-done="isAgentDone"
      :is-loading="isAgentLoading"
      :current-action="currentFocusAction"
      :logs="activeAgentLogs"
      :total-days="editablePlan?.totalDays"
      :route-count="routeItemCount"
      :budget="editablePlan?.estimatedCost"
      :token-stats="agentTokenStats"
      :tool-stats="agentToolStats"
      :execution-logs="agentExecutionLogs"
      :show-logs="showFullSummary"
      @toggle-logs="toggleSummaryDetail"
    />

    <!-- Chat Panel -->
    <aside class="chat-panel" v-if="showChatPanel && !agentPanelCollapsed">
      <div class="chat-header">
        <span>💬 AI 对话</span>
        <button class="chat-close-btn" @click="showChatPanel = false">✕</button>
      </div>
      <div class="chat-body" ref="chatBodyRef">
        <div v-if="chatMessages.length === 0 && !isChatLoading" class="chat-empty">
          <p>👋 你好！我是你的旅行助手</p>
          <p class="chat-empty-sub">问我任何关于旅行的问题，我会尽力帮你解答～</p>
        </div>
        <div v-for="(msg, i) in chatMessages" :key="i" class="chat-bubble" :class="msg.role">
          <span class="chat-role">{{ msg.role === 'user' ? '你' : '🤖' }}</span>
          <div class="chat-content" v-html="renderChatContent(msg.content)"></div>
        </div>
        <div v-if="isChatLoading && streamingContent" class="chat-bubble assistant">
          <span class="chat-role">🤖</span>
          <div class="chat-content" v-html="renderChatContent(streamingContent)"></div>
        </div>
        <div v-if="isChatLoading && !streamingContent" class="chat-typing">
          <span></span><span></span><span></span>
        </div>
      </div>
      <div class="chat-input-area">
        <input
          v-model="chatInput"
          class="chat-input"
          placeholder="输入消息，按回车发送..."
          :disabled="isChatLoading"
          @keydown.enter.exact.prevent="sendChatMessage"
        />
        <button class="chat-send-btn" :disabled="isChatLoading || !chatInput.trim()" @click="sendChatMessage">
          发送
        </button>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  generateItineraryStream,
  modifyItineraryStream,
  getItineraryDetail,
  saveItinerary,
  updateItinerary
} from '../../api/itinerary'
import type { ItineraryPlan, ItineraryRequest, PhaseData, GenerationStats } from '../../api/itinerary'
import { linkItinerary, getConversationByItineraryId, getConversationDetail } from '../../api/conversation'
import { goBackByAppRule } from '../../composables/useBackHandler'
import { listMySkills, type SkillVO } from '../../api/skill'
import { streamChat, type ChatMessage } from '../../api/chat'
import AgentPanel from './AgentPanel.vue'
import './agent-panel-styles.css'

const router = useRouter()
const route = useRoute()
const goBack = () => goBackByAppRule(router)

interface LogEntry {
  type: 'phase' | 'thought' | 'tool'
  message: string
  phaseType?: string
}

// ===== 新增响应式状态 =====
const showForm = ref(true)
const agentPanelCollapsed = ref(false)
const showChatPanel = ref(false)
const refineHistory = ref<Array<{ prompt: string; success: boolean }>>([])

const budgetLabel = computed(() => {
  const map: Record<string, string> = { low: '经济', medium: '舒适', high: '豪华' }
  return map[request.budgetLevel] || '经济'
})

// ===== 原有状态 =====
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
const generationStats = ref<GenerationStats | null>(null)
const refineStats = ref<GenerationStats | null>(null)
const loadedStats = ref<GenerationStats | null>(null)
const activeConversationId = ref<string | null>(null)
const mySkills = ref<SkillVO[]>([])

// Chat state
const chatMessages = ref<Array<{ role: string; content: string }>>([])
const chatInput = ref('')
const isChatLoading = ref(false)
const streamingContent = ref('')
const chatConversationId = ref<string | null>(localStorage.getItem('chatConvId'))
const chatBodyRef = ref<HTMLElement | null>(null)

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

// ===== 日志压缩 =====
const currentFocusAction = computed(() => {
  const logs = activeAgentLogs.value
  if (logs.length === 0) return ''
  for (let i = logs.length - 1; i >= 0; i--) {
    const entry = logs[i]
    if (entry.type === 'tool' && entry.message.includes('正在')) {
      return entry.message
    }
    if (entry.type === 'thought') {
      const text = entry.message.length > 50 ? entry.message.substring(0, 50) + '...' : entry.message
      return '思考: ' + text
    }
  }
  return ''
})

const activeStats = computed(() => {
  if (generationStats.value) return generationStats.value
  if (refineStats.value) return refineStats.value
  if (loadedStats.value) return loadedStats.value
  return null
})




// AgentPanel 组件所需的计算属性（不再依赖 step）
const isAgentGenerating = computed(() => isLoadingGenerate.value || isRefining.value)
const isAgentDone = computed(() => !!editablePlan.value && !isRefining.value)
const isAgentLoading = computed(() => activeAgentLogs.value.length === 0 && isAgentGenerating.value)

const agentTokenStats = computed(() => {
  if (!activeStats.value) return undefined
  return {
    promptTokens: activeStats.value.totalPromptTokens,
    completionTokens: activeStats.value.totalCompletionTokens,
    totalTokens: activeStats.value.totalTokens,
    apiCalls: activeStats.value.planCalls + activeStats.value.executeCalls + activeStats.value.verifyCalls
  }
})

const agentToolStats = computed(() => {
  if (!activeStats.value?.toolCalls) return undefined
  // 按工具名分组去重，合并为带调用次数的汇总记录
  const grouped = new Map<string, { name: string; displayName: string; count: number; successCount: number }>()
  for (const tc of activeStats.value.toolCalls) {
    const key = tc.displayName || tc.name
    if (!grouped.has(key)) {
      grouped.set(key, { name: key, displayName: key, count: 0, successCount: 0 })
    }
    const entry = grouped.get(key)!
    entry.count++
    if (tc.status === 'success') entry.successCount++
  }
  return Array.from(grouped.values()).map(entry => ({
    ...entry,
    status: entry.successCount > 0 ? 'success' as const : 'skipped' as const
  }))
})

const agentExecutionLogs = computed(() => {
  if (!generatingLogs.value || generatingLogs.value.length === 0) return undefined
  return generatingLogs.value
    .filter(entry => !isEmptyEntry(entry))
    .map(entry => {
      if (entry.type === 'phase') {
        return {
          type: 'phase',
          icon: entry.phaseType?.endsWith('_END') ? '✓' : '›',
          text: entry.message
        }
      } else if (entry.type === 'thought') {
        return {
          type: 'thought',
          icon: '💡',
          text: isJsonMessage(entry.message) ? cleanJsonPreview(entry.message) : truncateText(entry.message, 120)
        }
      } else if (entry.type === 'tool') {
        return {
          type: 'tool',
          icon: getToolIcon(entry.message),
          text: extractToolName(entry.message)
        }
      }
      return { type: 'unknown', icon: '', text: entry.message }
    })
})

watch(activeAgentLogs, async () => {
  await nextTick()
  if (agentLogRef.value) {
    agentLogRef.value.scrollTop = agentLogRef.value.scrollHeight
  }
})

watch(
  () => activeAgentLogs.value.length,
  (newLen, oldLen) => {
    if (newLen < (oldLen || 0)) {
      manuallyToggledThoughts.value = new Set()
      expandedActiveThoughts.value = new Set()
      return
    }
    const logs = activeAgentLogs.value
    const thoughtIndices: number[] = []
    for (let i = 0; i < logs.length; i++) {
      if (logs[i].type === 'thought' && !isEmptyEntry(logs[i])) {
        thoughtIndices.push(i)
      }
    }
    // 只自动展开最近的1条思考过程（减少信息过载）
    const recentOne = thoughtIndices.slice(-1)
    const newExpanded = new Set(expandedActiveThoughts.value)
    for (const idx of recentOne) {
      if (!manuallyToggledThoughts.value.has(idx)) {
        newExpanded.add(idx)
      }
    }
    for (const idx of thoughtIndices.slice(0, -1)) {
      if (!manuallyToggledThoughts.value.has(idx)) {
        newExpanded.delete(idx)
      }
    }
    expandedActiveThoughts.value = newExpanded
  }
)

watch(currentAgentPhase, (newPhase, oldPhase) => {
  if (newPhase !== oldPhase && oldPhase) {
    const newExpanded = new Set(expandedGroups.value)
    const oldGroup = oldPhase.replace(/_START$|_END$/, '')
    newExpanded.delete(oldGroup)
    const newGroup = newPhase.replace(/_START$|_END$/, '')
    newExpanded.add(newGroup)
    expandedGroups.value = newExpanded
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

// ===== 分组显示 =====
const expandedGroups = ref<Set<string>>(new Set(['PLAN']))

const expandedActiveThoughts = ref<Set<number>>(new Set())
const manuallyToggledThoughts = ref<Set<number>>(new Set())






const getToolIcon = (message: string): string => {
  if (message.includes('weather')) return '🌤️'
  if (message.includes('search') || message.includes('Search')) return '🔍'
  if (message.includes('direction') || message.includes('路线')) return '🗺️'
  if (message.includes('distance')) return '📏'
  if (message.includes('schema') || message.includes('地图')) return '🗺️'
  if (message.includes('info') || message.includes('destination')) return 'ℹ️'
  return '🔧'
}

const extractToolName = (message: string): string => {
  const match = message.match(/Invoking tool: (\w+)/)
  if (match && match[1]) {
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
      search_destination_info: '目的地信息'
    }
    return nameMap[match[1]] || match[1]
  }
  return message.substring(0, 30) + (message.length > 30 ? '...' : '')
}



// ===== 位置信息和地图功能 =====
const hasLocation = (item: { location?: { lat?: number | string; lng?: number | string; name?: string } }): boolean => {
  return !!(item.location && (item.location.lat || item.location.lng || item.location.name))
}

const formatCoordinates = (item: { location?: { lat?: number | string; lng?: number | string } }): string | null => {
  if (!item.location) return null
  const lat = Number(item.location.lat)
  const lng = Number(item.location.lng)
  if (isNaN(lat) || isNaN(lng)) return null
  return `(${lat.toFixed(4)}, ${lng.toFixed(4)})`
}

const openMap = (item: { location?: { lat?: number | string; lng?: number | string; name?: string }; name?: string }) => {
  if (!item.location) {
    alert('该地点暂无坐标信息')
    return
  }
  const lat = Number(item.location.lat || 0)
  const lng = Number(item.location.lng || 0)
  if (!lat || !lng) {
    alert('该地点暂无坐标信息')
    return
  }
  const name = encodeURIComponent(item.location.name || item.name || '目的地')
  window.open(`https://uri.amap.com/marker?position=${lng},${lat}&name=${name}`, '_blank')
}

// ===== 生成过程总结 =====
const showFullSummary = ref(false)

const toggleSummaryDetail = () => {
  showFullSummary.value = !showFullSummary.value
}



// 检查是否为空白的entry
const isEmptyEntry = (entry: LogEntry): boolean => {
  if (!entry.message) return true
  const trimmed = entry.message.trim()
  if (trimmed.length === 0) return true
  if (trimmed === 'THOUGHT:') return true
  if (trimmed.startsWith('THOUGHT:') && trimmed.replace('THOUGHT:', '').trim().length === 0) return true
  return false
}


const isJsonMessage = (msg: string): boolean => {
  if (!msg) return false
  const trimmed = msg.trim()
  return (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
         (trimmed.startsWith('[') && trimmed.endsWith(']'))
}

// 清理JSON预览文本
const cleanJsonPreview = (msg: string): string => {
  try {
    const parsed = JSON.parse(msg)
    if (parsed.status) return `状态: ${parsed.status}`
    if (parsed.summary) return String(parsed.summary)
    if (parsed.message) return String(parsed.message)
    return JSON.stringify(parsed).substring(0, 100)
  } catch {
    return msg
  }
}

// 文本截断函数
const truncateText = (text: string, maxLength: number): string => {
  if (!text) return ''
  let msg = text
  if (msg.startsWith('THOUGHT:')) {
    msg = msg.replace('THOUGHT:', '').trim()
  }
  if (msg.length <= maxLength) return msg
  return msg.substring(0, maxLength) + '...'
}

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
  days: 3, interests: [], budgetLevel: 'medium', additionalNotes: '', skillIds: []
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

const toggleSkill = (skillId: string) => {
  if (!request.skillIds) request.skillIds = []
  const idx = request.skillIds.indexOf(skillId)
  if (idx >= 0) request.skillIds.splice(idx, 1)
  else request.skillIds.push(skillId)
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

const mergeRefinedPlan = (original: ItineraryPlan, refined: ItineraryPlan): ItineraryPlan => {
  // LLM's totalDays is the authoritative intent signal:
  //   reduced   → AI decided user wants to delete an entire day
  //   increased → AI decided user wants to add days
  //   unchanged → AI decided user only wants item-level changes
  const aiDecidedDayDeletion = refined.totalDays < original.totalDays
  const aiDecidedDayAddition = refined.totalDays > original.totalDays

  // Build a set of item names that exist in the refined plan
  const refinedItemNames = new Set<string>()
  for (const day of refined.dailyPlans) {
    for (const item of day.items) {
      if (item.name) refinedItemNames.add(item.name)
    }
  }

  // Start with the original days, removing only items the LLM deleted
  const resultDays = original.dailyPlans.map(origDay => ({
    dayNumber: origDay.dayNumber,
    items: origDay.items.filter(origItem => {
      if (!origItem.name) return true
      return refinedItemNames.has(origItem.name)
    })
  }))

  // Apply new/modified items from the refined plan
  for (const refinedDay of refined.dailyPlans) {
    if (refinedDay.dayNumber == null || refinedDay.dayNumber <= 0) continue

    if (aiDecidedDayAddition && refinedDay.dayNumber > original.totalDays) {
      // Brand new day that didn't exist in the original
      resultDays.push({
        dayNumber: refinedDay.dayNumber,
        items: refinedDay.items.map(item => ({
          type: normalizeRouteType(item.type),
          name: item.name || '未命名路线项',
          description: item.description || '暂无描述',
          location: item.location || { lat: 0, lng: 0, name: '' }
        }))
      })
      continue
    }

    const existingDay = resultDays.find(d => d.dayNumber === refinedDay.dayNumber)
    if (existingDay) {
      const existingNames = new Set(existingDay.items.map(i => i.name))
      for (const refinedItem of refinedDay.items) {
        if (refinedItem.name && !existingNames.has(refinedItem.name)) {
          existingDay.items.push({
            type: normalizeRouteType(refinedItem.type),
            name: refinedItem.name,
            description: refinedItem.description || '暂无描述',
            location: refinedItem.location || { lat: 0, lng: 0, name: '' }
          })
        }
      }
    }
  }

  let finalDays: typeof resultDays
  if (aiDecidedDayDeletion) {
    // AI decided to delete a day — trust it
    const llmDayNumbers = new Set(refined.dailyPlans.map(d => d.dayNumber))
    finalDays = resultDays.filter(d => llmDayNumbers.has(d.dayNumber))
    finalDays = finalDays.map((d, i) => ({ ...d, dayNumber: i + 1 }))
  } else if (aiDecidedDayAddition) {
    // AI decided to add days — keep all, renumber
    finalDays = resultDays
      .filter(d => d.items.length > 0)
      .map((d, i) => ({ ...d, dayNumber: i + 1 }))
  } else {
    // Item-level changes: remove days that became empty from item deletion
    finalDays = resultDays
      .filter(d => d.items.length > 0)
      .map((d, i) => ({ ...d, dayNumber: i + 1 }))
  }

  return {
    title: refined.title || original.title,
    estimatedCost: refined.estimatedCost || original.estimatedCost,
    totalDays: finalDays.length,
    dailyPlans: finalDays.length > 0 ? finalDays : original.dailyPlans
  }
}

const handleGenerate = async () => {
  if (editablePlan.value) {
    try {
      await ElMessageBox.confirm(
        '当前已有编辑中的行程方案，重新生成将覆盖现有内容。是否继续？',
        '确认重新生成',
        { confirmButtonText: '重新生成', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  errorMessage.value = ''
  generatingLogs.value = []
  currentPhase.value = ''
  generationStats.value = null
  activeConversationId.value = null
  const ve = validateRequest(); if (ve) { errorMessage.value = ve; return }
  isLoadingGenerate.value = true
  try {
    await generateItineraryStream(
      { ...request },
      (phase: PhaseData) => { generatingLogs.value.push({ type: 'phase', message: phase.message, phaseType: phase.type }); currentPhase.value = phase.type },
      (msg: string) => { generatingLogs.value.push({ type: 'thought', message: msg }) },
      (msg: string) => { generatingLogs.value.push({ type: 'tool', message: getLogMessage(msg) }) },
      (plan) => { editablePlan.value = normalizePlan(plan); isLoadingGenerate.value = false },
      (error) => { errorMessage.value = error.message || '生成报错，请稍后重试。'; isLoadingGenerate.value = false },
      (stats) => { generationStats.value = stats },
      (convId) => { activeConversationId.value = convId }
    )
  } catch (error: any) { errorMessage.value = '生成失败，请稍后重试。'; isLoadingGenerate.value = false }
}

const handleAiRefineQuick = () => {
  handleAiRefine()
}

const handleAiRefine = async () => {
  if (!refinePrompt.value.trim() || !editablePlan.value) return
  const promptText = refinePrompt.value.trim()
  isRefining.value = true; refineLogs.value = []; refineCurrentPhase.value = ''; refineStats.value = null
  const originalPlan = { ...editablePlan.value }

  try {
    await modifyItineraryStream(
      { ...request },
      { ...editablePlan.value },
      promptText,
      activeConversationId.value,
      (phase: PhaseData) => { refineLogs.value.push({ type: 'phase', message: phase.message, phaseType: phase.type }); refineCurrentPhase.value = phase.type },
      (msg: string) => { refineLogs.value.push({ type: 'thought', message: msg }) },
      (msg: string) => { refineLogs.value.push({ type: 'tool', message: getLogMessage(msg) }) },
      (plan: ItineraryPlan) => {
        editablePlan.value = mergeRefinedPlan(originalPlan, normalizePlan(plan))
        isRefining.value = false
        refinePrompt.value = ''
        refineHistory.value.push({ prompt: promptText, success: true })
      },
      (error: any) => {
        ElMessageBox.alert(error.message || '修改报错', 'AI修改失败', { type: 'error' })
        isRefining.value = false
        refineHistory.value.push({ prompt: promptText, success: false })
      },
      (stats: GenerationStats) => { refineStats.value = stats }
    )
  } catch (error: any) {
    ElMessageBox.alert(error.message || '修改失败', 'AI修改失败', { type: 'error' })
    isRefining.value = false
    refineHistory.value.push({ prompt: promptText, success: false })
  }
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

const loadDetailForEdit = async () => {
  if (!itineraryId.value) return
  isLoadingGenerate.value = true; errorMessage.value = ''
  try {
    const r = await getItineraryDetail(itineraryId.value)
    const plan = normalizePlan(r.data.data.plan)
    editablePlan.value = plan
    loadedStats.value = r.data.data.stats || null
    savedItineraryId.value = r.data.data.id
    if (plan.destination) request.destination = plan.destination
    if (plan.totalDays) request.days = plan.totalDays
    try {
      const convRes = await getConversationByItineraryId(itineraryId.value)
      if (convRes.data?.code === '200' && convRes.data?.data?.id) {
        activeConversationId.value = convRes.data.data.id
      }
    } catch { }
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
  const stats = generationStats.value || refineStats.value || loadedStats.value || undefined
  try {
    if (isEditMode.value) {
      await updateItinerary(itineraryId.value, { ...editablePlan.value, destination: request.destination.trim(), stats })
      savedItineraryId.value = itineraryId.value
    } else {
      const r = await saveItinerary({ ...editablePlan.value, destination: request.destination.trim(), dailyPlans: editablePlan.value.dailyPlans, stats })
      savedItineraryId.value = r.data.data
      if (activeConversationId.value && savedItineraryId.value) {
        linkItinerary(activeConversationId.value, savedItineraryId.value).catch(() => {})
      }
    }
    ElMessage.success(isEditMode.value ? '方案已更新！' : '方案已保存！')
  } catch { errorMessage.value = '保存失败，请稍后重试。' } finally { isSaving.value = false }
}

onMounted(async () => {
  if (route.query.destination) request.destination = String(route.query.destination)
  if (route.query.convId) activeConversationId.value = String(route.query.convId)
  if (itineraryId.value) await loadDetailForEdit()
  fetchMySkills()
})

async function fetchMySkills() {
  try {
    const r = await listMySkills()
    mySkills.value = (r as any).data || []
  } catch { /* ignore */ }
}

// ===== Chat functions =====
async function loadChatHistory() {
  const convId = chatConversationId.value
  if (!convId) return
  try {
    const res = await getConversationDetail(convId)
    if (res.data?.data?.messages) {
      const msgs = res.data.data.messages
        .filter((m: AgentMessageVO) => m.role === 'user' || m.role === 'assistant')
        .map((m: AgentMessageVO) => ({ role: m.role, content: m.content }))
      if (msgs.length > 0) {
        chatMessages.value = msgs
      }
    }
  } catch { /* ignore */ }
}

function toggleChatPanel() {
  showChatPanel.value = !showChatPanel.value
  if (showChatPanel.value) {
    agentPanelCollapsed.value = false
    if (chatMessages.value.length === 0) {
      loadChatHistory()
    }
    nextTick(() => scrollChatToBottom())
  }
}

function scrollChatToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

function renderChatContent(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

async function sendChatMessage() {
  const msg = chatInput.value.trim()
  if (!msg || isChatLoading.value) return

  chatMessages.value.push({ role: 'user', content: msg })
  chatInput.value = ''
  streamingContent.value = ''
  isChatLoading.value = true
  scrollChatToBottom()

  try {
    await streamChat(msg, chatConversationId.value, {
      onToken: (token: string) => {
        streamingContent.value += token
        scrollChatToBottom()
      },
      onDone: (data: { conversationId: string; messageId: string }) => {
        if (streamingContent.value) {
          chatMessages.value.push({ role: 'assistant', content: streamingContent.value })
          streamingContent.value = ''
        }
        if (data.conversationId) {
          chatConversationId.value = data.conversationId
          localStorage.setItem('chatConvId', data.conversationId)
        }
        isChatLoading.value = false
        scrollChatToBottom()
      },
      onError: (error: string) => {
        chatMessages.value.push({ role: 'assistant', content: '抱歉，出了点问题：' + error })
        isChatLoading.value = false
      }
    })
  } catch {
    chatMessages.value.push({ role: 'assistant', content: '抱歉，网络出问题了，请稍后重试。' })
    isChatLoading.value = false
  }
}
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

/* Header extras */
.top-bar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.top-bar-toggle-form,
.top-bar-toggle-panel {
  padding: 4px 10px;
  border: 1px solid rgba(51, 133, 255, 0.15);
  border-radius: 8px;
  background: transparent;
  font-size: 12px;
  color: #5B7FA5;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.top-bar-toggle-form:hover,
.top-bar-toggle-panel:hover {
  border-color: #3385FF;
  color: #3385FF;
}
.top-bar-toggle-form.active {
  background: rgba(51, 133, 255, 0.08);
  color: #3385FF;
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
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1E3A5F;
}

/* Form collapsible */
.form-section {
  transition: all 0.25s ease;
  overflow: hidden;
}
.form-collapsed {
  margin-bottom: 12px;
  padding: 0;
}
.form-collapsed .form-section-body {
  display: none;
}
.form-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: linear-gradient(135deg, #F8FAFD 0%, #F0F4F8 100%);
  border: 1px solid rgba(51, 133, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.form-section-header:hover {
  border-color: rgba(51, 133, 255, 0.2);
}
.form-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #1E3A5F;
  display: flex;
  align-items: center;
  gap: 6px;
}
.form-section-title svg {
  color: #3385FF;
}
.form-section-arrow {
  font-size: 11px;
  color: #94A3B8;
}

.edit-plan-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
}

.eph-left {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.eph-title-wrap { margin: 0; }

.eph-title {
  width: 100%;
  border: none;
  background: transparent;
  padding: 2px 4px;
  font-size: 22px;
  font-weight: 800;
  color: #1E3A5F;
  outline: none;
  border-radius: 6px;
  transition: background 0.15s, box-shadow 0.15s;
}

.eph-title::placeholder { color: #B8C9E0; font-weight: 600; }
.eph-title:focus {
  background: rgba(51, 133, 255, 0.04);
  box-shadow: 0 0 0 3px rgba(51, 133, 255, 0.08);
}

.eph-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.eph-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.eph-tag svg { color: #94a3b8; }

.eph-tag-route {
  color: #3385FF;
  background: #F0F7FF;
  border-color: rgba(51, 133, 255, 0.12);
}

.eph-tag-route svg { color: #3385FF; }

.eph-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.eph-btn {
  padding: 10px 18px;
  font-size: 13px;
  white-space: nowrap;
}

.eph-conv-link {
  background: transparent;
  border: 1.5px solid rgba(255, 149, 0, 0.2);
  color: #D97706;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.eph-conv-link:hover {
  background: rgba(255, 149, 0, 0.05);
  border-color: rgba(255, 149, 0, 0.4);
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
  outline: none;
}

.large-input:focus {
  background: rgba(51, 133, 255, 0.03);
  border-radius: 6px;
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

.chip-skill {
  background: #faf5ff;
  color: #7c3aed;
  border-color: #e9d5ff;
}

.chip-skill.active {
  background: #7c3aed;
  color: #fff;
  box-shadow: 0 3px 10px rgba(124, 58, 237, 0.3);
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
  outline: none;
}

textarea:focus { background: rgba(51, 133, 255, 0.03); border-radius: 6px; }

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
  background: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  margin-bottom: 24px;
  border: 1px solid rgba(99,102,241,0.12);
  box-shadow: 0 1px 6px rgba(99,102,241,0.04);
}

.ai-refine-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.ai-refine-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  color: #6366f1;
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
  border: 1px solid rgba(99,102,241,0.15);
}

.ai-refine-badge svg { color: #6366f1; }

.ai-refine-hint {
  font-size: 12px;
  color: #94a3b8;
}

/* AI chat-style input */
.ai-chat-input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.ai-chat-input-row textarea {
  flex: 1;
  min-height: 40px;
}
.ai-chat-input-row .ai-send-btn {
  flex-shrink: 0;
  height: 40px;
  padding: 0 16px;
}

.ai-refine-box textarea {
  width: 100%;
  border: none;
  background: transparent;
  padding: 0;
  resize: vertical;
  min-height: 60px;
  font-family: inherit;
  color: #334155;
  font-size: 14px;
  line-height: 1.7;
  outline: none;
}

.ai-refine-box textarea::placeholder { color: #c4cdd8; }
.ai-refine-box textarea:focus {
  background: rgba(99, 102, 241, 0.03);
  border-radius: 6px;
}

.ai-send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* Refine history */
.refine-history {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 120px;
  overflow-y: auto;
}
.refine-history-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  padding: 4px 8px;
  background: #F8FAFD;
  border-radius: 6px;
}
.rh-user {
  color: #3385FF;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rh-status { font-size: 10px; }
.rh-status.ok { color: #22C55E; }
.rh-status.fail { color: #EF4444; }

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
  margin-bottom: 28px;
}

.day-header-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #fff 0%, #fafbff 100%);
  border-radius: 12px;
  padding: 14px 18px;
  margin-bottom: 16px;
  border: 1px solid rgba(51, 133, 255, 0.08);
  box-shadow: 0 1px 4px rgba(0,0,0,0.02);
}

.day-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.day-index-badge {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(51, 133, 255, 0.3);
}

.day-header-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.day-header-label {
  font-size: 15px;
  font-weight: 700;
  color: #1E3A5F;
}

.day-header-sub {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
}

.btn-add-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1px dashed rgba(51, 133, 255, 0.25);
  background: transparent;
  color: #3385FF;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-add-item:hover {
  background: #F0F7FF;
  border-color: #3385FF;
  border-style: solid;
}

.btn-add-item svg { flex-shrink: 0; }

.timeline-list {
  padding-left: 12px;
  border-left: 2px dashed rgba(51, 133, 255, 0.1);
  margin-left: 20px;
  padding-top: 8px;
}

.timeline-row {
  position: relative;
  margin-bottom: 14px;
  border-radius: 12px;
  border-left: 3px solid transparent;
  transition: all 0.2s;
}

.type-border-spot { border-left-color: #0284c7; }
.type-border-food { border-left-color: #ea580c; }
.type-border-traffic { border-left-color: #7c3aed; }
.type-border-hotel { border-left-color: #2563eb; }
.type-border-relax { border-left-color: #16a34a; }
.type-border-shopping { border-left-color: #db2777; }
.type-border-default { border-left-color: #94a3b8; }

.timeline-row:hover {
  box-shadow: 0 4px 16px rgba(51,133,255,0.08);
  transform: translateY(-1px);
}

.timeline-thread {
  position: absolute;
  left: -32px;
  top: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.node-circle {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  background: #fff;
  border: 3px solid #F0F4F8;
  z-index: 5;
  transition: transform 0.2s, box-shadow 0.2s;
}

.timeline-row:hover .node-circle {
  transform: scale(1.08);
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.timeline-card-wrap {
  margin-left: 20px;
}

.timeline-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(51, 133, 255, 0.06);
  transition: box-shadow 0.2s;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-type-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.tag-spot { background: #e0f2fe; color: #0369a1; }
.tag-food { background: #ffedd5; color: #c2410c; }
.tag-traffic { background: #f3e8ff; color: #7c3aed; }
.tag-hotel { background: #dbeafe; color: #1d4ed8; }
.tag-relax { background: #dcfce7; color: #15803d; }
.tag-shopping { background: #fce7f3; color: #be185d; }
.tag-default { background: #f1f5f9; color: #475569; }

.card-title {
  font-weight: 700;
  font-size: 14px;
  color: #1E3A5F;
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

.map-link-btn {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 6px;
  background: #F0F7FF;
  color: #3385FF;
  cursor: pointer;
  border: none;
  white-space: nowrap;
  transition: all 0.15s;
}

.map-link-btn:hover {
  background: #E0EFFF;
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
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.mini-select:focus, .mini-input:focus, .mini-textarea:focus {
  border-color: #3385FF;
  box-shadow: 0 0 0 3px rgba(51, 133, 255, 0.08);
}

.mini-select {
  flex: 0 0 80px;
}

.empty-day-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  padding: 28px 20px;
  border: 2px dashed rgba(51, 133, 255, 0.1);
  border-radius: 12px;
  margin-left: 20px;
  background: #fafcff;
}

.bg-spot { background: #e0f2fe; color: #0284c7; }
.bg-food { background: #ffedd5; color: #ea580c; }
.bg-traffic { background: #f3e8ff; color: #7c3aed; }
.bg-hotel { background: #dbeafe; color: #2563eb; }
.bg-relax { background: #dcfce7; color: #16a34a; }
.bg-shopping { background: #fce7f3; color: #db2777; }
.bg-default { background: #f1f5f9; color: #475569; }

/* Panel collapse */
.customize-main.with-panel {
  /* keep existing layout when panel is visible */
}

/* ===== Chat Panel ===== */
.chat-panel {
  width: 360px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid rgba(51, 133, 255, 0.1);
  background: #fff;
  flex-shrink: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 16px;
  font-weight: 700;
  color: #1a1a2e;
}

.chat-close-btn {
  border: none;
  background: none;
  font-size: 18px;
  color: #909399;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all .2s;
}
.chat-close-btn:hover { background: #f5f5f5; color: #333; }

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-empty {
  text-align: center;
  padding: 40px 20px;
  color: #909399;
}
.chat-empty p { margin: 0 0 6px; font-size: 15px; }
.chat-empty-sub { font-size: 13px !important; }

.chat-bubble {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 90%;
}

.chat-bubble.user {
  align-self: flex-end;
}

.chat-bubble.assistant {
  align-self: flex-start;
}

.chat-role {
  font-size: 11px;
  color: #909399;
  padding: 0 2px;
}

.chat-content {
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.chat-bubble.user .chat-content {
  background: linear-gradient(135deg, #8AC4FF, #3385FF);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat-bubble.assistant .chat-content {
  background: #f5f7fa;
  color: #303133;
  border-bottom-left-radius: 4px;
}

.chat-typing {
  align-self: flex-start;
  display: flex;
  gap: 5px;
  padding: 12px 16px;
}
.chat-typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: chat-bounce 1.4s infinite ease-in-out both;
}
.chat-typing span:nth-child(1) { animation-delay: -0.32s; }
.chat-typing span:nth-child(2) { animation-delay: -0.16s; }

@keyframes chat-bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.chat-input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.chat-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 20px;
  font-size: 14px;
  outline: none;
  transition: border-color .2s;
}
.chat-input:focus { border-color: #8AC4FF; }

.chat-send-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, #8AC4FF, #3385FF);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity .2s;
}
.chat-send-btn:hover { opacity: .9; }
.chat-send-btn:disabled { opacity: .5; cursor: not-allowed; }

.top-bar-chat-btn {
  border-color: #e9d5ff !important;
  color: #7c3aed !important;
}
.top-bar-chat-btn.active {
  background: #faf5ff !important;
  border-color: #7c3aed !important;
}
</style>
