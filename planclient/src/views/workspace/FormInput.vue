<template>
  <div class="ws-form-wrapper" :class="{ collapsed: isCollapsed }">
    <div v-if="isCollapsed" class="ws-form-summary" @click="isCollapsed = false">
      <div class="ws-summary-info">
        <span class="ws-summary-dest">📍 {{ destination || '未填写' }}</span>
        <span class="ws-summary-meta">{{ days }}天 · {{ budgetLabel }}</span>
        <span v-if="interests.length" class="ws-summary-tags">{{ interests.length }}项偏好</span>
      </div>
      <button class="ws-summary-toggle" @click.stop="isCollapsed = false">
        <span class="ws-summary-arrow">▾</span> 展开
      </button>
    </div>

    <template v-else>
      <div class="ws-form-header">
        <span class="ws-form-header-title">📋 行程规划</span>
        <button v-if="destination || interests.length" class="ws-form-collapse-btn" @click="isCollapsed = true" title="收起表单">收起 ▴</button>
      </div>

      <div class="ws-form-panel">
        <!-- 目的地（独占一行，更宽敞） -->
        <div class="ws-form-row ws-form-dest-row">
          <label class="ws-field-dest">
            <span class="ws-label-sm">目的地</span>
            <input v-model.trim="destination" class="ws-input-dest" placeholder="你想去哪里？" />
          </label>
        </div>

        <!-- 天数 + 预算（同一行） -->
        <div class="ws-form-row ws-form-meta-row">
          <div class="field-inline-group">
            <span class="ws-label-sm">天数</span>
            <div class="ws-stepper-compact">
              <button class="ws-step-btn-c" @click="days = Math.max(1, days - 1)">−</button>
              <span class="ws-step-val-c">{{ days }}</span>
              <button class="ws-step-btn-c" @click="days = Math.min(30, days + 1)">+</button>
            </div>
          </div>
          <div class="field-inline-group">
            <span class="ws-label-sm">预算</span>
            <div class="ws-segments-compact">
              <button v-for="opt in budgetOptions" :key="opt.value" class="ws-seg-item-c" :class="{ active: budgetLevel === opt.value }" @click="budgetLevel = opt.value">{{ opt.label }}</button>
            </div>
          </div>
        </div>

        <div class="ws-form-row ws-form-interests-row">
          <span class="ws-label-sm ws-label-inline">偏好</span>
          <div class="ws-chips-compact">
            <button v-for="item in interestOptions" :key="item.value" class="ws-chip-c" :class="{ active: interests.includes(item.value) }" @click="toggleInterest(item.value)">{{ item.label }}</button>
          </div>
        </div>

        <div v-if="skills.length > 0" class="ws-form-row ws-form-skills-row">
          <span class="ws-label-sm ws-label-inline">技能</span>
          <div class="ws-chips-compact">
            <button v-for="skill in skills" :key="skill.id" class="ws-chip-c ws-chip-skill" :class="{ active: skillIds.includes(skill.id) }" @click="toggleSkill(skill.id)">{{ skill.name }}</button>
          </div>
        </div>

        <div class="ws-form-row ws-form-action-row">
          <label class="ws-field-notes">
            <textarea v-model.trim="notes" class="ws-input-notes" rows="1" placeholder="补充说明（选填）..."></textarea>
          </label>
          <button class="primary ws-generate-btn-c" :disabled="!destination || isGenerating" @click="handleSubmit">{{ isGenerating ? '规划中...' : '生成方案' }}</button>
          <button class="ws-switch-chat-btn-c" @click="$emit('switchMode', 'chat')" title="切换到对话模式">对话 →</button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

interface SkillVO {
  id: string
  name: string
}

interface FormRequest {
  destination: string
  days: number
  budgetLevel: string
  interests: string[]
  additionalNotes: string
  skillIds?: string[]
}

const props = defineProps<{ isGenerating?: boolean; skills?: SkillVO[] }>()

const emit = defineEmits<{
  submit: [request: FormRequest]
  switchMode: [mode: 'chat']
}>()

const isCollapsed = ref(false)
const destination = ref('')
const days = ref(3)
const budgetLevel = ref('medium')
const interests = ref<string[]>([])
const skillIds = ref<string[]>([])
const notes = ref('')

const budgetOptions = [
  { label: '经济', value: 'low' },
  { label: '舒适', value: 'medium' },
  { label: '豪华', value: 'high' },
]

const interestOptions = [
  { label: '🏛️ 文化古迹', value: 'culture' },
  { label: '🍜 美食探店', value: 'food' },
  { label: '🌿 自然风光', value: 'nature' },
  { label: '🎢 主题乐园', value: 'amusement' },
  { label: '🛍️ 购物休闲', value: 'shopping' },
  { label: '📸 拍照打卡', value: 'photography' },
  { label: '👨‍👩‍👧 亲子出行', value: 'family' },
  { label: '🎭 艺术展览', value: 'art' },
]

const budgetLabel = computed(() => budgetOptions.find(o => o.value === budgetLevel.value)?.label || '舒适')

function toggleInterest(value: string) {
  const idx = interests.value.indexOf(value)
  if (idx >= 0) interests.value.splice(idx, 1)
  else interests.value.push(value)
}

function toggleSkill(skillId: string) {
  const idx = skillIds.value.indexOf(skillId)
  if (idx >= 0) skillIds.value.splice(idx, 1)
  else skillIds.value.push(skillId)
}

function handleSubmit() {
  if (!destination.value.trim() || props.isGenerating) return
  emit('submit', {
    destination: destination.value.trim(),
    days: days.value,
    budgetLevel: budgetLevel.value,
    interests: [...interests.value],
    additionalNotes: notes.value.trim(),
    skillIds: [...skillIds.value],
  })
  isCollapsed.value = true
}
</script>

<style scoped>
.ws-form-wrapper {
  position: relative;
  background: #fff;
  border: 1px solid rgba(51, 133, 255, 0.1);
  border-radius: 16px;
  padding: 14px 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06), 0 1px 4px rgba(0, 0, 0, 0.03);
  transition: box-shadow 0.2s;
}
.ws-form-wrapper:hover {
  box-shadow: 0 6px 32px rgba(51, 133, 255, 0.1), 0 2px 6px rgba(0, 0, 0, 0.04);
}
.ws-form-wrapper.collapsed .ws-form-panel { display: none; }
.ws-form-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; padding: 0 2px; }
.ws-form-header-title { font-size: 12px; font-weight: 700; color: #1E3A5F; }
.ws-form-collapse-btn { padding: 3px 8px; border-radius: 6px; border: 1px solid rgba(51, 133, 255, 0.1); background: transparent; color: #94A3B8; font-size: 11px; font-weight: 500; cursor: pointer; transition: all 0.15s; }
.ws-form-collapse-btn:hover { color: #5B8AC0; border-color: rgba(51, 133, 255, 0.2); }
.ws-form-summary { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: linear-gradient(135deg, #F5F9FF 0%, #FAFCFF 100%); border: 1px solid rgba(51, 133, 255, 0.09); border-radius: 12px; cursor: pointer; transition: all 0.18s ease; }
.ws-form-summary:hover { border-color: rgba(51, 133, 255, 0.18); }
.ws-summary-info { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; flex-wrap: wrap; }
.ws-summary-dest { font-size: 13px; font-weight: 600; color: #1E3A5F; }
.ws-summary-meta { font-size: 12px; color: #6B8CBE; }
.ws-summary-tags { font-size: 11px; padding: 1px 7px; border-radius: 8px; background: rgba(51, 133, 255, 0.07); color: #3385FF; font-weight: 500; }
.ws-summary-toggle { padding: 4px 10px; border-radius: 6px; border: 1px solid rgba(51, 133, 255, 0.12); background: #fff; color: #3385FF; font-size: 12px; font-weight: 500; cursor: pointer; white-space: nowrap; flex-shrink: 0; }
.ws-summary-toggle:hover { background: #3385FF; color: #fff; }
.ws-summary-arrow { font-size: 11px; margin-right: 2px; }
.ws-form-panel { display: flex; flex-direction: column; gap: 10px; }
.ws-form-row { display: flex; align-items: center; gap: 10px; background: #FAFCFF; border: 1px solid rgba(51, 133, 255, 0.08); border-radius: 12px; padding: 10px 14px; transition: border-color 0.2s; }
.ws-form-row:hover { border-color: rgba(51, 133, 255, 0.14); }
.ws-form-dest-row { padding: 12px 14px; }
.ws-form-meta-row { gap: 16px; justify-content: flex-start; }
.ws-field-dest { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.ws-input-dest { width: 100%; padding: 9px 13px; border: 1.5px solid #D8E2EF; border-radius: 10px; background: #FAFCFF; font-family: inherit; font-size: 14px; color: #1E3A5F; outline: none; box-sizing: border-box; font-weight: 500; transition: border-color 0.2s, box-shadow 0.2s; }
.ws-input-dest:focus { border-color: #3385FF; box-shadow: 0 0 0 3px rgba(51, 133, 255, 0.08); background: #fff; }
.ws-input-dest::placeholder { color: #A8BDD0; font-weight: 400; }
.field-inline-group { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.ws-label-sm { font-size: 11px; font-weight: 600; color: #7A8FA6; white-space: nowrap; letter-spacing: 0.3px; }
.ws-label-inline { flex-shrink: 0; min-width: 24px; }
.ws-stepper-compact { display: flex; align-items: center; gap: 0; border: 1.5px solid #D8E2EF; border-radius: 8px; overflow: hidden; background: #fff; }
.ws-step-btn-c { width: 28px; height: 30px; display: flex; align-items: center; justify-content: center; border: none; background: #F5F7FA; color: #5B7FA5; font-size: 15px; cursor: pointer; transition: all 0.12s; }
.ws-step-btn-c:hover { background: #E4EBF5; color: #3385FF; }
.ws-step-btn-c:first-child { border-right: 1px solid #D8E2EF; }
.ws-step-btn-c:last-child { border-left: 1px solid #D8E2EF; }
.ws-step-val-c { min-width: 32px; text-align: center; font-size: 14px; font-weight: 700; color: #1E3A5F; }
.ws-segments-compact { display: flex; gap: 0; border: 1.5px solid #D8E2EF; border-radius: 8px; overflow: hidden; background: #fff; }
.ws-seg-item-c { padding: 6px 8px; border: none; background: #F5F7FA; color: #6B8CBE; font-size: 12px; font-weight: 600; cursor: pointer; transition: all 0.15s; text-align: center; white-space: nowrap; }
.ws-seg-item-c:not(:last-child) { border-right: 1px solid #D8E2EF; }
.ws-seg-item-c.active { background: linear-gradient(135deg, rgba(51, 133, 255, 0.1), rgba(26, 111, 255, 0.06)); color: #1A6FFF; font-weight: 700; }
.ws-seg-item-c:hover:not(.active) { background: #EAF0F8; color: #4A72BA; }
.ws-form-interests-row { gap: 10px; align-items: flex-start; padding-top: 10px; padding-bottom: 10px; }
.ws-form-skills-row { gap: 10px; align-items: flex-start; padding-top: 10px; padding-bottom: 10px; }
.ws-chips-compact { display: flex; flex-wrap: wrap; gap: 6px; flex: 1; }
.ws-chip-c { padding: 5px 12px; border: 1.5px solid #DDE6F0; border-radius: 20px; background: #F8FBFF; color: #6B8CBE; font-size: 12px; font-weight: 500; cursor: pointer; transition: all 0.16s ease; white-space: nowrap; }
.ws-chip-c:hover { border-color: #99BBEA; color: #4A72BA; }
.ws-chip-c.active { background: linear-gradient(135deg, #EEF3FC, #F0F5FF); border-color: #3385FF; color: #1A6FFF; font-weight: 600; box-shadow: 0 1px 4px rgba(51, 133, 255, 0.1); }
.ws-chip-skill { background: #faf5ff; color: #7c3aed; border-color: #e9d5ff; }
.ws-chip-skill.active { background: #7c3aed; color: #fff; box-shadow: 0 3px 10px rgba(124, 58, 237, 0.3); }
.ws-form-action-row { gap: 10px; padding: 8px 14px; }
.ws-field-notes { flex: 1; min-width: 0; }
.ws-input-notes { width: 100%; padding: 8px 12px; border: 1.5px solid #E8EFF8; border-radius: 9px; background: #FAFBFD; font-family: inherit; font-size: 13px; color: #1E3A5F; outline: none; resize: vertical; min-height: 34px; max-height: 80px; transition: border-color 0.2s, box-shadow 0.2s; box-sizing: border-box; line-height: 1.5; }
.ws-input-notes:focus { border-color: #3385FF; box-shadow: 0 0 0 3px rgba(51, 133, 255, 0.06); background: #fff; }
.ws-input-notes::placeholder { color: #B0C8DE; }
.ws-generate-btn-c { flex-shrink: 0; padding: 8px 20px; border-radius: 10px; border: none; background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%); color: #fff; font-size: 13px; font-weight: 700; cursor: pointer; transition: all 0.2s; box-shadow: 0 2px 10px rgba(51, 133, 255, 0.25); white-space: nowrap; }
.ws-generate-btn-c:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 16px rgba(51, 133, 255, 0.35); }
.ws-generate-btn-c:active:not(:disabled) { transform: translateY(0); }
.ws-generate-btn-c:disabled { background: linear-gradient(135deg, #93c5fd 0%, #60a5fa 100%); cursor: not-allowed; box-shadow: none; }
.ws-switch-chat-btn-c { flex-shrink: 0; padding: 8px 12px; border: 1.5px dashed #C5DAF0; border-radius: 9px; background: transparent; color: #7A98BD; font-size: 12px; cursor: pointer; white-space: nowrap; transition: all 0.16s; }
.ws-switch-chat-btn-c:hover { background: rgba(51, 133, 255, 0.03); border-style: solid; border-color: #99BBEA; color: #3385FF; }
</style>