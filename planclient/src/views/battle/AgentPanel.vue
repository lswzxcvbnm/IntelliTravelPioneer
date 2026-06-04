<template>
  <!-- 折叠态：悬浮小药丸 -->
  <div v-if="collapsed" class="ap-collapsed-pill" @click="$emit('toggle-collapse')">
    <span class="ap-collapsed-icon">{{ collapsedIcon }}</span>
    <span class="ap-collapsed-label">{{ statusText }}</span>
  </div>

  <!-- 展开态：完整面板 -->
  <aside v-else class="agent-panel" :class="{ 'is-generating': isGenerating, 'is-done': isDone }">
    <!-- 头部状态栏 -->
    <header class="ap-head">
      <div class="ap-head-status">
        <span class="ap-head-dot" :class="statusClass"></span>
        <span class="ap-head-title">{{ statusText }}</span>
      </div>
      <div class="ap-head-actions" v-if="isDone">
        <button class="ap-head-btn" @click="$emit('toggle-logs')">
          {{ showLogs ? '隐藏日志' : '查看日志' }}
        </button>
      </div>
    </header>

    <!-- 生成中状态 -->
    <template v-if="isGenerating">
      <!-- 进度指示器 -->
      <div class="ap-progress">
        <div class="ap-progress-steps">
          <div 
            v-for="(step, index) in progressSteps" 
            :key="step.id"
            class="ap-step"
            :class="{ 
              'active': step.status === 'active', 
              'completed': step.status === 'completed',
              'pending': step.status === 'pending'
            }"
          >
            <div class="ap-step-indicator">
              <span v-if="step.status === 'completed'" class="ap-step-check">✓</span>
              <span v-else class="ap-step-number">{{ index + 1 }}</span>
            </div>
            <span class="ap-step-label">{{ step.label }}</span>
          </div>
        </div>
        <div class="ap-progress-bar">
          <div class="ap-progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>
      </div>

      <!-- 当前动作 -->
      <div class="ap-action" v-show="isLoading || currentAction">
        <div class="ap-action-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
          </svg>
        </div>
        <div class="ap-action-content">
          <span class="ap-action-label">当前操作</span>
          <transition name="action-fade" mode="out-in">
            <span class="ap-action-text" :key="currentAction || 'idle'">{{ currentAction || '等待 AI 响应...' }}</span>
          </transition>
        </div>
      </div>

      <!-- 日志容器 -->
      <div class="ap-body" ref="logsContainer">
        <template v-if="groupedLogs.length > 0">
          <div v-for="(group, gi) in groupedLogs" :key="'g'+gi" class="ap-group">
            <div class="ap-group-header" @click="toggleGroupExpand(group.phase)">
              <div class="ap-group-node" :class="{ 
                'active': expandedGroups.has(group.phase),
                'completed': group.entries.some(e => e.phaseType?.endsWith('_END'))
              }">
                <span v-if="group.entries.some(e => e.phaseType?.endsWith('_END'))" class="ap-group-check">✓</span>
                <span v-else class="ap-group-num">{{ gi + 1 }}</span>
              </div>
              <span class="ap-group-name">{{ getPhaseGroupName(group.phase) }}</span>
              <span class="ap-group-count">{{ group.entries.filter(e => e.type === 'tool').length }} 工具</span>
              <span class="ap-group-arrow" :class="{ open: expandedGroups.has(group.phase) }">›</span>
            </div>

            <transition name="ap-slide">
              <div v-if="expandedGroups.has(group.phase)" class="ap-entries">
                <div v-for="(entry, ei) in group.entries" :key="ei" class="ap-entry">
                  <template v-if="entry.type === 'thought' && !isEmptyEntry(entry)">
                    <div class="ap-thought" @click="toggleThoughtExpand(getGlobalIndex(gi, ei))">
                      <div class="ap-thought-bar"></div>
                      <div class="ap-thought-inner">
                        <div class="ap-thought-header">
                          <span class="ap-thought-icon">
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                              <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"/>
                            </svg>
                          </span>
                          <span class="ap-thought-label">推理</span>
                          <span class="ap-thought-toggle">
                            {{ isThoughtExpanded(getGlobalIndex(gi, ei)) ? '收起' : '展开' }}
                          </span>
                        </div>
                        <div 
                          class="ap-thought-content" 
                          :class="{ collapsed: !isThoughtExpanded(getGlobalIndex(gi, ei)) }"
                          v-html="formatThoughtMessage(entry.message)"
                        ></div>
                      </div>
                    </div>
                  </template>

                  <template v-else-if="entry.type === 'tool' && !isEmptyEntry(entry)">
                    <div class="ap-tool">
                      <span class="ap-tool-dot" :class="getToolStatusClass(entry.message)"></span>
                      <span class="ap-tool-icon">{{ getToolIcon(entry.message) }}</span>
                      <span class="ap-tool-name">{{ extractToolName(entry.message) }}</span>
                      <span class="ap-tool-status" :class="getToolStatusClass(entry.message)">
                        {{ getToolStatusText(entry.message) }}
                      </span>
                    </div>
                  </template>
                </div>
              </div>
            </transition>
          </div>
        </template>

        <!-- 加载指示器 -->
        <div class="ap-loading" v-if="isLoading">
          <div class="ap-loading-dot"></div>
          <div class="ap-loading-dot"></div>
          <div class="ap-loading-dot"></div>
        </div>
      </div>

      <!-- 统计信息 -->
      <div class="ap-stats" v-if="activeAgentLogs.length > 0">
        <div class="ap-stat-item">
          <span class="ap-stat-number">{{ thoughtLogCount }}</span>
          <span class="ap-stat-label">思考</span>
        </div>
        <div class="ap-stat-divider"></div>
        <div class="ap-stat-item">
          <span class="ap-stat-number">{{ toolLogCount }}</span>
          <span class="ap-stat-label">工具</span>
        </div>
        <div class="ap-stat-divider"></div>
        <div class="ap-stat-item">
          <span class="ap-stat-number">{{ phaseLogCount }}</span>
          <span class="ap-stat-label">阶段</span>
        </div>
      </div>
    </template>

    <!-- 完成状态 -->
    <template v-else-if="isDone">
      <!-- 完成指标（紧凑摘要条） -->
      <div class="ap-done-summary-bar">
        <div class="ap-summary-item" v-if="totalDays">
          <span class="ap-summary-value">{{ totalDays }}</span>
          <span class="ap-summary-label">天</span>
        </div>
        <div class="ap-summary-item" v-if="routeCount">
          <span class="ap-summary-value">{{ routeCount }}</span>
          <span class="ap-summary-label">地点</span>
        </div>
        <div class="ap-summary-item" v-if="budget">
          <span class="ap-summary-value">¥{{ formattedBudget }}</span>
          <span class="ap-summary-label">预算</span>
        </div>
        <div class="ap-summary-item" v-if="totalTokens">
          <span class="ap-summary-value">{{ formatCompactNumber(totalTokens) }}</span>
          <span class="ap-summary-label">Tokens</span>
        </div>
        <div class="ap-summary-spacer"></div>
        <button
          class="ap-replay-toggle"
          :class="{ active: showReplay }"
          @click="showReplay = !showReplay"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          {{ showReplay ? '收起过程' : '过程回放' }}
        </button>
      </div>

      <!-- 过程回放（可折叠） -->
      <transition name="replay-slide">
        <div v-if="showReplay" class="ap-replay-panel">
          <!-- Token 消耗 -->
          <div class="ap-done-section" v-if="tokenStats">
            <div class="ap-done-section-head">
              <h4 class="ap-done-section-title">Token 消耗</h4>
              <span class="ap-done-section-badge">{{ tokenStats.apiCalls }} 次调用</span>
            </div>
            <div class="ap-token-bar">
              <div class="ap-token-segment prompt" :style="{ width: tokenPromptPercent + '%' }"></div>
              <div class="ap-token-segment completion" :style="{ width: tokenCompletionPercent + '%' }"></div>
            </div>
            <div class="ap-token-legend">
              <div class="ap-token-item">
                <span class="ap-token-dot prompt"></span>
                <span class="ap-token-text">提示 {{ tokenStats.promptTokens.toLocaleString() }}</span>
              </div>
              <div class="ap-token-item">
                <span class="ap-token-dot completion"></span>
                <span class="ap-token-text">生成 {{ tokenStats.completionTokens.toLocaleString() }}</span>
              </div>
            </div>
          </div>

          <!-- 工具调用统计 -->
          <div class="ap-done-section" v-if="toolStats && toolStats.length > 0">
            <div class=" ap-done-section-head">
              <h4 class="ap-done-section-title">工具调用</h4>
              <span class="ap-done-section-badge">{{ toolTotalCalls }} 次调用</span>
            </div>
            <div class="ap-done-tools">
              <div
                v-for="tool in toolStats"
                :key="tool.name"
                class="ap-done-tool"
              >
                <span class="ap-tool-dot" :class="tool.status"></span>
                <span class="ap-done-tool-name">{{ tool.displayName }}</span>
                <span class="ap-done-tool-count">×{{ tool.count }}</span>
              </div>
            </div>
            <div class="ap-tool-stats-row">
              <span class="ap-tool-stat success">{{ toolSuccessCount }} 成功</span>
              <span class="ap-tool-stat skipped" v-if="toolSkippedCount > 0">{{ toolSkippedCount }} 跳过</span>
            </div>
          </div>

          <!-- 执行记录时间线 -->
          <div class="ap-done-section" v-if="executionLogs && executionLogs.length > 0">
            <div class="ap-done-section-head">
              <h4 class="ap-done-section-title">执行过程</h4>
              <span class="ap-done-section-badge">{{ executionLogs.length }} 条记录</span>
            </div>
            <div class="ap-timeline">
              <div
                v-for="(log, index) in executionLogs"
                :key="index"
                class="ap-timeline-item"
                :class="log.type"
                @click="toggleTimelineItem(index)"
              >
                <div class="ap-timeline-marker">
                  <span class="ap-timeline-icon">{{ log.icon }}</span>
                </div>
                <div class="ap-timeline-content">
                  <span class="ap-timeline-text" :class="{ expanded: expandedTimelineItems.has(index) }">
                    {{ log.text }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- 无执行记录时的占位 -->
          <div v-else class="ap-replay-empty">
            <p>本次生成暂无可回放的执行记录</p>
          </div>
        </div>
      </transition>

      <!-- 收起态：快捷操作入口 -->
      <div v-if="!showReplay" class="ap-quick-actions">
        <div class="ap-quick-action" v-if="toolStats && toolStats.length > 0" @click="showReplay = true">
          <span>🔧 {{ toolTotalCalls }} 次工具调用</span>
        </div>
        <div class="ap-quick-action" v-if="tokenStats" @click="showReplay = true">
          <span>⚡ {{ formatCompactNumber(totalTokens) }} Tokens</span>
        </div>
      </div>
    </template>

    <!-- 空闲状态 -->
    <template v-else>
      <div class="ap-idle">
        <div class="ap-idle-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="3" y="11" width="18" height="10" rx="2"/>
            <circle cx="12" cy="5" r="2"/>
            <path d="M12 7v4"/>
            <line x1="8" y1="16" x2="8" y2="16"/>
            <line x1="16" y1="16" x2="16" y2="16"/>
          </svg>
        </div>
        <h3 class="ap-idle-title">AI 旅行规划助手</h3>
        <p class="ap-idle-description">填写左侧需求表单，点击<strong>"生成 AI 方案"</strong></p>
        <div class="ap-idle-features">
          <div class="ap-feature">
            <span class="ap-feature-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>
            </span>
            <span class="ap-feature-text">实时展示 Agent 思考过程</span>
          </div>
          <div class="ap-feature">
            <span class="ap-feature-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
              </svg>
            </span>
            <span class="ap-feature-text">自动调用地图、天气等工具</span>
          </div>
          <div class="ap-feature">
            <span class="ap-feature-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              </svg>
            </span>
            <span class="ap-feature-text">智能验证行程合理性</span>
          </div>
        </div>
      </div>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'

// 类型定义
interface LogEntry {
  type: 'phase' | 'thought' | 'tool'
  message: string
  phaseType?: string
}

interface ProgressStep {
  id: string
  label: string
  status: 'pending' | 'active' | 'completed'
}

interface ToolStat {
  name: string
  displayName: string
  status: 'success' | 'skipped'
  count: number
  successCount: number
}

interface TokenStats {
  promptTokens: number
  completionTokens: number
  totalTokens: number
  apiCalls: number
}

// Props
const props = defineProps<{
  isGenerating: boolean
  isDone: boolean
  isLoading: boolean
  currentAction?: string
  logs: LogEntry[]
  totalDays?: number
  routeCount?: number
  budget?: number
  tokenStats?: TokenStats
  toolStats?: ToolStat[]
  executionLogs?: Array<{ type: string; icon: string; text: string }>
  showLogs?: boolean
  collapsed?: boolean
}>()

// Emits
const emit = defineEmits<{
  'toggle-logs': []
  'toggle-collapse': []
}>()

// 状态
const logsContainer = ref<HTMLElement | null>(null)
const expandedGroups = ref<Set<string>>(new Set())
const expandedThoughts = ref<Set<number>>(new Set())
const showReplay = ref(false)
const expandedTimelineItems = ref<Set<number>>(new Set())

// 计算属性
const statusClass = computed(() => {
  if (props.isGenerating) return 'pulse'
  if (props.isDone) return 'done'
  return 'idle'
})

const statusText = computed(() => {
  if (props.isGenerating) return 'Agent 正在生成方案'
  if (props.isDone) return '生成完成'
  return 'Agent 就绪'
})

const collapsedIcon = computed(() => {
  if (props.isGenerating) return '🎯'
  if (props.isDone) return '✅'
  return '🤖'
})

const progressSteps = computed<ProgressStep[]>(() => {
  const steps: ProgressStep[] = [
    { id: 'plan', label: '规划', status: 'pending' },
    { id: 'execute', label: '执行', status: 'pending' },
    { id: 'verify', label: '验证', status: 'pending' }
  ]
  
  // 根据当前日志确定进度
  const phaseLogs = props.logs.filter(log => log.type === 'phase')
  if (phaseLogs.length > 0) {
    const lastPhase = phaseLogs[phaseLogs.length - 1]
    if (lastPhase.phaseType?.includes('PLAN')) {
      steps[0].status = 'active'
    } else if (lastPhase.phaseType?.includes('EXECUTE')) {
      steps[0].status = 'completed'
      steps[1].status = 'active'
    } else if (lastPhase.phaseType?.includes('VERIFY')) {
      steps[0].status = 'completed'
      steps[1].status = 'completed'
      steps[2].status = 'active'
    }
  }
  
  return steps
})

const progressPercent = computed(() => {
  const completedSteps = progressSteps.value.filter(step => step.status === 'completed').length
  const activeStep = progressSteps.value.find(step => step.status === 'active')
  const basePercent = (completedSteps / 3) * 100
  const activePercent = activeStep ? (1 / 3) * 50 : 0
  return basePercent + activePercent
})

const groupedLogs = computed(() => {
  const groups: Array<{ phase: string; entries: LogEntry[] }> = []
  let currentGroup: { phase: string; entries: LogEntry[] } | null = null
  
  props.logs.forEach(log => {
    if (log.type === 'phase') {
      if (currentGroup) {
        groups.push(currentGroup)
      }
      currentGroup = { phase: log.message, entries: [log] }
    } else if (currentGroup) {
      currentGroup.entries.push(log)
    }
  })
  
  if (currentGroup) {
    groups.push(currentGroup)
  }
  
  return groups
})

const activeAgentLogs = computed(() => props.logs)
const thoughtLogCount = computed(() => props.logs.filter(log => log.type === 'thought').length)
const toolLogCount = computed(() => props.logs.filter(log => log.type === 'tool').length)
const phaseLogCount = computed(() => props.logs.filter(log => log.type === 'phase').length)

const totalDays = computed(() => props.totalDays || 0)
const routeCount = computed(() => props.routeCount || 0)
const formattedBudget = computed(() => {
  if (!props.budget) return '-'
  return `¥${props.budget.toLocaleString()}`
})

const totalTokens = computed(() => props.tokenStats?.totalTokens || 0)

const tokenPromptPercent = computed(() => {
  if (!props.tokenStats) return 0
  return (props.tokenStats.promptTokens / props.tokenStats.totalTokens) * 100
})

const tokenCompletionPercent = computed(() => {
  if (!props.tokenStats) return 0
  return (props.tokenStats.completionTokens / props.tokenStats.totalTokens) * 100
})

const toolTotalCalls = computed(() =>
  props.toolStats?.reduce((sum, t) => sum + (t.count || 0), 0) || 0
)

const toolSuccessCount = computed(() =>
  props.toolStats?.filter(tool => tool.status === 'success').length || 0
)

const toolSkippedCount = computed(() =>
  props.toolStats?.filter(tool => tool.status === 'skipped').length || 0
)

// 方法
const toggleGroupExpand = (phase: string) => {
  if (expandedGroups.value.has(phase)) {
    expandedGroups.value.delete(phase)
  } else {
    expandedGroups.value.add(phase)
  }
}

const toggleThoughtExpand = (index: number) => {
  if (expandedThoughts.value.has(index)) {
    expandedThoughts.value.delete(index)
  } else {
    expandedThoughts.value.add(index)
  }
}

const isThoughtExpanded = (index: number) => expandedThoughts.value.has(index)

const toggleTimelineItem = (index: number) => {
  if (expandedTimelineItems.value.has(index)) {
    expandedTimelineItems.value.delete(index)
  } else {
    expandedTimelineItems.value.add(index)
  }
}

const formatCompactNumber = (n: number): string => {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

const isEmptyEntry = (entry: LogEntry) => !entry.message || entry.message.trim() === ''

const getPhaseGroupName = (phase: string) => {
  const phaseMap: Record<string, string> = {
    'PLAN_START': '规划阶段',
    'PLAN_END': '规划完成',
    'EXECUTE_START': '执行阶段',
    'EXECUTE_END': '执行完成',
    'VERIFY_START': '验证阶段',
    'VERIFY_END': '验证完成'
  }
  return phaseMap[phase] || phase
}

const getGlobalIndex = (groupIndex: number, entryIndex: number) => {
  let index = 0
  for (let i = 0; i < groupIndex; i++) {
    index += groupedLogs.value[i].entries.length
  }
  return index + entryIndex
}

const formatThoughtMessage = (message: string) => {
  if (!message) return ''
  // 简单的markdown格式化
  return message
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
}

const getToolStatusClass = (message: string) => {
  if (message.includes('成功') || message.includes('完成')) return 'status-success'
  if (message.includes('跳过')) return 'status-skipped'
  return 'status-running'
}

const getToolIcon = (message: string) => {
  const iconMap: Record<string, string> = {
    '天气': '🌤',
    '搜索': '🔍',
    '路线': '🗺',
    '距离': '📏',
    '地图': '📍',
    '信息': 'ℹ️'
  }
  
  for (const [key, icon] of Object.entries(iconMap)) {
    if (message.includes(key)) return icon
  }
  return '🔧'
}

const extractToolName = (message: string) => {
  // 提取工具名称
  const match = message.match(/正在(.+?)\.\.\./)
  return match ? match[1] : message
}

const getToolStatusText = (message: string) => {
  if (message.includes('成功') || message.includes('完成')) return '已完成'
  if (message.includes('跳过')) return '已跳过'
  return '执行中'
}

// 自动滚动到底部
watch(() => props.logs.length, async () => {
  await nextTick()
  if (logsContainer.value) {
    logsContainer.value.scrollTop = logsContainer.value.scrollHeight
  }
})
</script>

<style scoped>
.ap-done-tool-count {
  font-size: 11px;
  color: #94A3B8;
  background: #F1F5F9;
  padding: 1px 6px;
  border-radius: 8px;
  margin-left: 4px;
}

.action-fade-enter-active,
.action-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.action-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
.action-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ====== 完成态：过程回放 ====== */
.ap-done-summary-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #F0FDF4 0%, #ECFDF5 100%);
  border: 1px solid rgba(34, 197, 94, 0.15);
  border-radius: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.ap-summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.ap-summary-value {
  font-size: 16px;
  font-weight: 700;
  color: #166534;
}

.ap-summary-label {
  font-size: 10px;
  color: #6B8F71;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.ap-summary-spacer {
  flex: 1;
  min-width: 8px;
}

.ap-replay-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: 1.5px solid rgba(51, 133, 255, 0.25);
  border-radius: 20px;
  background: transparent;
  color: #3385FF;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.ap-replay-toggle:hover {
  background: rgba(51, 133, 255, 0.06);
  border-color: #3385FF;
}

.ap-replay-toggle.active {
  background: rgba(51, 133, 255, 0.08);
  border-color: #3385FF;
}

.ap-replay-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.replay-slide-enter-active,
.replay-slide-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.replay-slide-enter-from,
.replay-slide-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
  margin-bottom: 0;
}

.ap-timeline {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.ap-timeline-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.1s ease;
}

.ap-timeline-item:hover {
  background: rgba(51, 133, 255, 0.04);
}

.ap-timeline-marker {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #F1F5F9;
  font-size: 11px;
}

.ap-timeline-item.phase .ap-timeline-marker {
  background: rgba(99, 102, 241, 0.1);
}

.ap-timeline-item.tool .ap-timeline-marker {
  background: rgba(51, 133, 255, 0.08);
}

.ap-timeline-item.thought .ap-timeline-marker {
  background: rgba(245, 158, 11, 0.08);
}

.ap-timeline-icon {
  font-size: 11px;
}

.ap-timeline-content {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
}

.ap-timeline-text {
  font-size: 12px;
  color: #5B7FA5;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.ap-timeline-text.expanded {
  -webkit-line-clamp: unset;
  display: block;
}

.ap-replay-empty {
  text-align: center;
  padding: 20px 12px;
  color: #94A3B8;
  font-size: 13px;
}

.ap-quick-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.ap-quick-action {
  padding: 6px 12px;
  background: #FAFBFC;
  border: 1px solid rgba(51, 133, 255, 0.08);
  border-radius: 8px;
  font-size: 12px;
  color: #5B7FA5;
  cursor: pointer;
  transition: all 0.15s ease;
}

.ap-quick-action:hover {
  background: rgba(51, 133, 255, 0.05);
  border-color: rgba(51, 133, 255, 0.18);
  color: #3385FF;
}

/* ====== 折叠态小药丸 ====== */
.ap-collapsed-pill {
  position: fixed;
  right: 16px;
  bottom: 24px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: linear-gradient(135deg, #1A6FFF 0%, #3385FF 100%);
  color: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 12px rgba(51, 133, 255, 0.3);
  cursor: pointer;
  z-index: 100;
  transition: all 0.2s ease;
  animation: ap-pill-bounce 2s ease infinite;
}

.ap-collapsed-pill:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 16px rgba(51, 133, 255, 0.4);
}

.ap-collapsed-icon {
  font-size: 16px;
}

.ap-collapsed-label {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

@keyframes ap-pill-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}
</style>