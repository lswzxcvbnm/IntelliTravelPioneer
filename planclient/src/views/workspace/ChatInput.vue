<template>
  <div class="ws-chat-composer">
    <div class="ws-composer-inner">
      <textarea
        v-model="inputText"
        @keydown.enter.exact.prevent="handleSend"
        :placeholder="placeholder"
        rows="2"
        :disabled="disabled"
      ></textarea>
      <button
        class="primary ws-send-btn"
        @click="handleSend"
        :disabled="!canSend || disabled"
      >
        <span v-if="!isGenerating && !isRefining" class="ws-send-icon">↑</span>
        {{ isGenerating ? '生成中' : isRefining ? '优化中' : '' }}
      </button>
    </div>
  </div>
  <div class="ws-input-hint" v-if="showHint && !hasPlan && mode === 'chat'">
    用自然语言描述你的旅行需求，或切换到「表单模式」结构化输入
    <button class="ws-hint-switch" @click="$emit('switchMode', 'form')">→ 切换表单</button>
  </div>
  <div class="ws-input-hint" v-else-if="hasPlan">
    描述修改意见，AI 将调整当前行程（如：把第一天换成室内景点）
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  placeholder?: string
  disabled?: boolean
  isGenerating?: boolean
  isRefining?: boolean
  hasPlan?: boolean
  showHint?: boolean
  mode?: 'form' | 'chat'
}>()

const emit = defineEmits<{
  send: [text: string]
  switchMode: [mode: 'form' | 'chat']
}>()

const inputText = ref('')

const canSend = computed(() => {
  return inputText.value.trim().length > 0
})

function handleSend() {
  if (!canSend.value || props.disabled) return
  emit('send', inputText.value.trim())
  inputText.value = ''
}
</script>

<style scoped>
.ws-chat-composer {
  background: #fff;
  border: 1.5px solid #D8E2EF;
  border-radius: 18px;
  padding: 6px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04), 0 1px 3px rgba(0, 0, 0, 0.02);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.ws-chat-composer:focus-within {
  border-color: #3385FF;
  box-shadow: 0 4px 20px rgba(51, 133, 255, 0.1), 0 1px 3px rgba(0, 0, 0, 0.02);
}

.ws-composer-inner {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.ws-composer-inner textarea {
  flex: 1;
  min-height: 60px;
  max-height: 150px;
  padding: 12px 16px;
  border: 1.5px solid #EDF2F7;
  border-radius: 13px;
  background: #FAFCFF;
  font-family: inherit;
  font-size: 14px;
  color: #1E3A5F;
  line-height: 1.6;
  outline: none;
  resize: none;
  transition: border-color 0.2s, background 0.2s;
}
.ws-composer-inner textarea:focus {
  border-color: #C5DAF0;
  background: #fff;
}
.ws-composer-inner textarea::placeholder { color: #B0C4DE; }
.ws-composer-inner textarea:disabled { background: #F5F7FA; cursor: not-allowed; }

.ws-send-btn {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  min-width: 56px;
  padding: 0;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 3px 14px rgba(51, 133, 255, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
}

.ws-send-icon {
  font-size: 20px;
  line-height: 1;
  font-weight: 400;
}

.ws-send-btn:hover:not(:disabled) {
  transform: translateY(-1px) scale(1.04);
  box-shadow: 0 5px 20px rgba(51, 133, 255, 0.38);
}
.ws-send-btn:disabled {
  background: #93c5fd;
  cursor: not-allowed;
  box-shadow: none;
}

.ws-input-hint {
  font-size: 12px;
  color: #94A3B8;
  margin-top: 8px;
  padding-left: 2px;
}

.ws-hint-switch {
  color: #3385FF;
  background: none;
  border: none;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  padding: 0 4px;
  margin-left: 2px;
}
.ws-hint-switch:hover { text-decoration: underline; }
</style>