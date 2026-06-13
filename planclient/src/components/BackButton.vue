<template>
  <button
    type="button"
    class="back-btn"
    @click="handleBack"
    :class="buttonClass"
  >
    <slot>← 返回</slot>
  </button>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { goBackByAppRule } from '../composables/useBackHandler'

interface Props {
  /** 自定义按钮样式类 */
  buttonClass?: string
  /** 自定义返回逻辑，如果不提供则使用默认的 goBackByAppRule */
  customBack?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  buttonClass: '',
  customBack: undefined,
})

const router = useRouter()

const handleBack = () => {
  if (props.customBack) {
    props.customBack()
  } else {
    goBackByAppRule(router)
  }
}
</script>

<style scoped>
.back-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: #5B7FA5;
  padding: 8px 12px;
  border-radius: 6px;
  transition: all 0.15s ease;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.back-btn:hover {
  color: #3385FF;
  background: rgba(51, 133, 255, 0.04);
}

.back-btn:active {
  background: rgba(51, 133, 255, 0.08);
}

.back-btn:focus-visible {
  outline: 2px solid #3385FF;
  outline-offset: 2px;
}
</style>