<template>
  <div class="ws-inline-tools">
    <div class="ws-tools-card" @click="toggleExpand">
      <span class="ws-tools-icon">🚀</span>
      <span class="ws-tools-title">{{ previewText }}</span>
      <span class="ws-tools-toggle">{{ isExpanded ? '▲' : '▼' }}</span>
    </div>
    <transition name="tools-expand">
      <div v-if="isExpanded" class="ws-tools-body">
        <div
          v-for="(tc, index) in parsedCalls"
          :key="index"
          class="ws-tool-item"
          :class="{ 'ws-tool-failed': tc.status === 'failed' }"
        >
          <span class="ws-tool-dot" :class="tc.status"></span>
          <span class="ws-tool-icon">{{ tc.icon }}</span>
          <span class="ws-tool-name">{{ tc.displayName }}</span>
          <span v-if="tc.args" class="ws-tool-args">{{ tc.args }}</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { TOOL_NAME_MAP, TOOL_ICON_MAP, getToolDisplayName, getToolIcon } from '../../constants/tool'

interface ToolCall {
  name: string
  displayName: string
  icon: string
  status: string
  args?: string
}

const props = defineProps<{
  toolCalls: string
}>()

const isExpanded = ref(false)



const parsedCalls = computed<ToolCall[]>(() => {
  try {
    const parsed = JSON.parse(props.toolCalls)
    if (!Array.isArray(parsed)) return []
    return parsed.map((tc: any) => {
      const rawName = tc.name || tc.function?.name || ''
      return {
        name: rawName,
        displayName: getToolDisplayName(rawName),
        icon: getToolIcon(rawName),
        status: tc.status || 'success',
        args: formatArgs(tc.arguments || tc.function?.arguments)
      }
    })
  } catch {
    return []
  }
})

const previewText = computed(() => {
  return `🚀 ${parsedCalls.value.length} 次工具调用`
})

function formatArgs(args: any): string {
  if (typeof args === 'string') {
    try { args = JSON.parse(args) } catch {}
  }
  if (typeof args !== 'object' || args === null) return ''
  const keys = Object.keys(args).filter(k => k !== '__arg_name')
  return keys.slice(0, 2).map(k => `${k}: ${String(args[k]).slice(0, 30)}`).join(' | ')
}

function toggleExpand() {
  isExpanded.value = !isExpanded.value
}
</script>

<style scoped>
.ws-inline-tools { margin-top: 10px; padding-top: 10px; border-top: 1px solid rgba(51, 133, 255, 0.08); }

.ws-tools-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #FAFBFC;
  border: 1px solid rgba(51, 133, 255, 0.08);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-tools-card:hover { border-color: rgba(51, 133, 255, 0.2); }
.ws-tools-icon { font-size: 16px; }
.ws-tools-title { font-weight: 600; font-size: 12px; color: #5B7FA5; flex: 1; }
.ws-tools-toggle { font-size: 10px; color: #94A3B8; }

.ws-tools-body {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ws-tool-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  background: #fff;
  border: 1px solid rgba(51, 133, 255, 0.05);
  border-radius: 8px;
  font-size: 12px;
  transition: background 0.1s;
}
.ws-tool-item:hover { background: #F8FAFD; }
.ws-tool-item.ws-tool-failed { opacity: 0.6; background: #FFF5F5; border-color: rgba(229, 62, 62, 0.1); }

.ws-tool-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #22C55E;
}
.ws-tool-failed .ws-tool-dot { background: #EF4444; }

.ws-tool-icon { font-size: 15px; flex-shrink: 0; }
.ws-tool-name { font-weight: 600; color: #1E3A5F; white-space: nowrap; }
.ws-tool-args { color: #94A3B8; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }

.tools-expand-enter-active,
.tools-expand-leave-active { transition: all 0.25s ease; overflow: hidden; }
.tools-expand-enter-from,
.tools-expand-leave-to { opacity: 0; max-height: 0; margin-top: 0; }
</style>