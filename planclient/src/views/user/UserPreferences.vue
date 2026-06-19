<template>
  <div class="preferences-page">
    <div class="preferences-header">
      <div class="header-left">
        <button class="btn-back" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回</span>
        </button>
        <div>
          <h1>🎯 全局偏好设置</h1>
          <p class="subtitle">设置你的个性化偏好，AI将在所有对话中参考这些设置</p>
        </div>
      </div>
      <button class="btn-save" @click="saveAllPreferences" :disabled="saving">
        <el-icon v-if="saving"><Loading /></el-icon>
        <span>{{ saving ? '保存中...' : '保存设置' }}</span>
      </button>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-grid">
        <div v-for="i in 2" :key="i" class="skeleton-section">
          <div class="skel-title"></div>
          <div class="skel-options"></div>
        </div>
      </div>
    </div>

    <div v-else class="preferences-content">
      <!-- 左侧列 -->
      <div class="preferences-column">
        <!-- 回答风格 -->
        <div class="preference-section">
          <div class="section-header">
            <h3>💬 回答风格</h3>
            <p class="section-desc">选择你希望AI如何回答你的问题</p>
          </div>
          <div class="options-grid">
            <div 
              v-for="(desc, key) in categories.STYLE?.options" 
              :key="key"
              class="option-card"
              :class="{ active: isSelected('STYLE', key) }"
              @click="toggleOption('STYLE', key, desc)"
            >
              <div class="option-icon">{{ getStyleIcon(key) }}</div>
              <div class="option-info">
                <span class="option-name">{{ getStyleName(key) }}</span>
                <span class="option-desc">{{ desc }}</span>
              </div>
              <div class="option-check" v-if="isSelected('STYLE', key)">
                <el-icon><Check /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- 饮食偏好 -->
        <div class="preference-section">
          <div class="section-header">
            <h3>🍽️ 饮食偏好</h3>
            <p class="section-desc">让AI了解你的饮食限制和偏好</p>
          </div>
          <div class="options-grid">
            <div 
              v-for="(desc, key) in categories.DIET?.options" 
              :key="key"
              class="option-card"
              :class="{ active: isSelected('DIET', key) }"
              @click="toggleOption('DIET', key, desc)"
            >
              <div class="option-icon">{{ getDietIcon(key) }}</div>
              <div class="option-info">
                <span class="option-name">{{ getDietName(key) }}</span>
                <span class="option-desc">{{ desc }}</span>
              </div>
              <div class="option-check" v-if="isSelected('DIET', key)">
                <el-icon><Check /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧列 -->
      <div class="preferences-column">
        <!-- 体能水平 -->
        <div class="preference-section">
          <div class="section-header">
            <h3>🏃 体能水平</h3>
            <p class="section-desc">让AI根据你的体能安排行程强度</p>
          </div>
          <div class="options-grid">
            <div 
              v-for="(desc, key) in categories.FITNESS?.options" 
              :key="key"
              class="option-card"
              :class="{ active: isSelected('FITNESS', key) }"
              @click="toggleOption('FITNESS', key, desc)"
            >
              <div class="option-icon">{{ getFitnessIcon(key) }}</div>
              <div class="option-info">
                <span class="option-name">{{ getFitnessName(key) }}</span>
                <span class="option-desc">{{ desc }}</span>
              </div>
              <div class="option-check" v-if="isSelected('FITNESS', key)">
                <el-icon><Check /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- 特殊需求 -->
        <div class="preference-section">
          <div class="section-header">
            <h3>👨‍👩‍👧‍👦 特殊需求</h3>
            <p class="section-desc">如有特殊情况，请告知AI</p>
          </div>
          <div class="options-grid">
            <div 
              v-for="(desc, key) in categories.SPECIAL?.options" 
              :key="key"
              class="option-card"
              :class="{ active: isSelected('SPECIAL', key) }"
              @click="toggleOption('SPECIAL', key, desc)"
            >
              <div class="option-icon">{{ getSpecialIcon(key) }}</div>
              <div class="option-info">
                <span class="option-name">{{ getSpecialName(key) }}</span>
                <span class="option-desc">{{ desc }}</span>
              </div>
              <div class="option-check" v-if="isSelected('SPECIAL', key)">
                <el-icon><Check /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部自定义规则 -->
    <div class="custom-rules-section">
      <div class="preference-section">
        <div class="section-header">
          <h3>✏️ 自定义规则</h3>
          <p class="section-desc">添加你自己的个性化指令，让AI更了解你的需求</p>
        </div>
        <div class="custom-rules">
          <div v-for="(rule, index) in customRules" :key="index" class="custom-rule-item">
            <input 
              v-model="rule.value" 
              placeholder="例如：推荐景点时优先考虑有WiFi的地方" 
              class="form-input"
            />
            <button class="btn-remove" @click="removeCustomRule(index)">
              <el-icon><Delete /></el-icon>
            </button>
          </div>
          <button class="btn-add-rule" @click="addCustomRule">
            <el-icon><Plus /></el-icon>
            <span>添加自定义规则</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Check, Delete, Plus, ArrowLeft } from '@element-plus/icons-vue'
import { 
  getUserPreferences, 
  getPreferenceCategories, 
  saveOrUpdatePreference, 
  deletePreference,
  type UserGlobalPreferenceVO,
  type UserGlobalPreferenceSaveRequest
} from '../../api/preference'

const router = useRouter()

// 状态
const loading = ref(true)
const saving = ref(false)
const categories = ref<Record<string, any>>({})
const userPreferences = ref<UserGlobalPreferenceVO[]>([])
const selectedOptions = ref<Record<string, Set<string>>>({})
const customRules = ref<{ key: string; value: string }[]>([])

// 初始化
onMounted(async () => {
  try {
    const [categoriesRes, preferencesRes] = await Promise.all([
      getPreferenceCategories(),
      getUserPreferences()
    ])
    
    categories.value = categoriesRes.data
    userPreferences.value = preferencesRes.data
    
    // 初始化选中状态
    initSelectedOptions()
  } catch (error) {
    console.error('加载偏好设置失败:', error)
    ElMessage.error('加载偏好设置失败')
  } finally {
    loading.value = false
  }
})

// 初始化选中状态
const initSelectedOptions = () => {
  const grouped: Record<string, Set<string>> = {}
  
  userPreferences.value.forEach(pref => {
    if (!grouped[pref.category]) {
      grouped[pref.category] = new Set()
    }
    grouped[pref.category].add(pref.preferenceKey)
  })
  
  selectedOptions.value = grouped
  
  // 初始化自定义规则
  const customPrefs = userPreferences.value.filter(p => p.category === 'CUSTOM')
  customRules.value = customPrefs.map(p => ({
    key: p.preferenceKey,
    value: p.preferenceValue
  }))
  
  if (customRules.value.length === 0) {
    customRules.value = [{ key: 'rule_1', value: '' }]
  }
}

// 检查是否选中
const isSelected = (category: string, key: string | number) => {
  return selectedOptions.value[category]?.has(String(key)) || false
}

// 切换选项
const toggleOption = (category: string, key: string | number, _value: string) => {
  if (!selectedOptions.value[category]) {
    selectedOptions.value[category] = new Set()
  }
  
  const stringKey = String(key)
  if (selectedOptions.value[category].has(stringKey)) {
    selectedOptions.value[category].delete(stringKey)
  } else {
    selectedOptions.value[category].add(stringKey)
  }
}

// 添加自定义规则
const addCustomRule = () => {
  customRules.value.push({ 
    key: `rule_${Date.now()}`, 
    value: '' 
  })
}

// 移除自定义规则
const removeCustomRule = (index: number) => {
  customRules.value.splice(index, 1)
}

// 保存所有偏好
const saveAllPreferences = async () => {
  saving.value = true
  
  try {
    // 收集所有需要保存的偏好
    const toSave: UserGlobalPreferenceSaveRequest[] = []
    const toDelete: { category: string; key: string }[] = []
    
    // 处理预设类别
    for (const category of ['STYLE', 'DIET', 'FITNESS', 'SPECIAL']) {
      const selectedKeys = selectedOptions.value[category] || new Set()
      const existingPrefs = userPreferences.value.filter(p => p.category === category)
      
      // 需要新增的
      selectedKeys.forEach(key => {
        const existing = existingPrefs.find(p => p.preferenceKey === key)
        if (!existing) {
          const optionDesc = categories.value[category]?.options[key] || key
          toSave.push({
            category,
            preferenceKey: key,
            preferenceValue: optionDesc,
            enabled: true
          })
        }
      })
      
      // 需要删除的
      existingPrefs.forEach(pref => {
        if (!selectedKeys.has(pref.preferenceKey)) {
          toDelete.push({ category, key: pref.preferenceKey })
        }
      })
    }
    
    // 处理自定义规则
    const existingCustoms = userPreferences.value.filter(p => p.category === 'CUSTOM')
    const validRules = customRules.value.filter(r => r.value.trim())
    
    // 删除空的自定义规则
    existingCustoms.forEach(pref => {
      const stillExists = validRules.find(r => r.key === pref.preferenceKey)
      if (!stillExists) {
        toDelete.push({ category: 'CUSTOM', key: pref.preferenceKey })
      }
    })
    
    // 保存有效的自定义规则
    validRules.forEach(rule => {
      const existing = existingCustoms.find(p => p.preferenceKey === rule.key)
      if (!existing || existing.preferenceValue !== rule.value) {
        toSave.push({
          category: 'CUSTOM',
          preferenceKey: rule.key,
          preferenceValue: rule.value,
          enabled: true
        })
      }
    })
    
    // 执行保存和删除
    const promises = [
      ...toSave.map(pref => saveOrUpdatePreference(pref)),
      ...toDelete.map(item => deletePreference(item.category, item.key))
    ]
    
    await Promise.all(promises)
    
    // 重新加载数据
    const preferencesRes = await getUserPreferences()
    userPreferences.value = preferencesRes.data
    initSelectedOptions()
    
    ElMessage.success('偏好设置已保存')
  } catch (error) {
    console.error('保存偏好设置失败:', error)
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

// 图标和名称映射
const getStyleIcon = (key: string | number) => {
  const icons: Record<string, string> = {
    brief: '📝',
    detailed: '📖',
    professional: '🎓',
    casual: '😊'
  }
  return icons[String(key)] || '💬'
}

const getStyleName = (key: string | number) => {
  const names: Record<string, string> = {
    brief: '简略',
    detailed: '详细',
    professional: '专业',
    casual: '轻松'
  }
  return names[String(key)] || String(key)
}

const getDietIcon = (key: string | number) => {
  const icons: Record<string, string> = {
    vegetarian: '🥬',
    vegan: '🌱',
    halal: '🍖',
    no_spicy: '🌶️',
    gluten_free: '🌾',
    seafood_allergy: '🦐'
  }
  return icons[String(key)] || '🍽️'
}

const getDietName = (key: string | number) => {
  const names: Record<string, string> = {
    vegetarian: '素食',
    vegan: '纯素',
    halal: '清真',
    no_spicy: '不吃辣',
    gluten_free: '无麸质',
    seafood_allergy: '海鲜过敏'
  }
  return names[String(key)] || String(key)
}

const getFitnessIcon = (key: string | number) => {
  const icons: Record<string, string> = {
    relaxed: '🧘',
    moderate: '🚶',
    active: '🏃',
    challenging: '🏋️'
  }
  return icons[String(key)] || '🏃'
}

const getFitnessName = (key: string | number) => {
  const names: Record<string, string> = {
    relaxed: '轻松',
    moderate: '适中',
    active: '活跃',
    challenging: '挑战'
  }
  return names[String(key)] || String(key)
}

const getSpecialIcon = (key: string | number) => {
  const icons: Record<string, string> = {
    elderly: '👴',
    children: '👶',
    wheelchair: '♿',
    pregnant: '🤰',
    pet: '🐾'
  }
  return icons[String(key)] || '👥'
}

const getSpecialName = (key: string | number) => {
  const names: Record<string, string> = {
    elderly: '携老人',
    children: '携儿童',
    wheelchair: '轮椅',
    pregnant: '孕妇',
    pet: '携宠物'
  }
  return names[String(key)] || String(key)
}
</script>

<style scoped>
.preferences-page {
  width: 100%;
  max-width: 1400px;
  min-height: calc(100vh - 48px);
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.preferences-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.btn-back {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #f5f5f5;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-back:hover {
  background: #e8e8e8;
  color: #333;
}

.preferences-header h1 {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 4px 0;
}

.subtitle {
  font-size: 13px;
  color: #666;
  margin: 0;
}

.btn-save {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.btn-save:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-save:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.loading-state {
  flex: 1;
  overflow: hidden;
}

.loading-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  height: 100%;
}

.skeleton-section {
  background: #f5f5f5;
  border-radius: 12px;
  padding: 24px;
}

.skel-title {
  width: 120px;
  height: 20px;
  background: #e0e0e0;
  border-radius: 4px;
  margin-bottom: 16px;
}

.skel-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skel-options::before {
  content: '';
  display: block;
  height: 60px;
  background: #e0e0e0;
  border-radius: 8px;
}

.preferences-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  flex: 1;
  align-content: start;
  margin-bottom: 8px;
}

.custom-rules-section {
  flex-shrink: 0;
  width: 100%;
}

.preferences-column {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.preference-section {
  background: white;
  border-radius: 10px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  width: 100%;
}

.section-header {
  margin-bottom: 8px;
}

.section-header h3 {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 2px 0;
}

.section-desc {
  font-size: 11px;
  color: #888;
  margin: 0;
}

.options-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 100%;
}

.option-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #f8f9fa;
  border: 2px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.option-card:hover {
  background: #f0f2ff;
  border-color: #e0e5ff;
}

.option-card.active {
  background: #f0f2ff;
  border-color: #667eea;
}

.option-icon {
  font-size: 22px;
  flex-shrink: 0;
  width: 28px;
  text-align: center;
}

.option-info {
  flex: 1;
  min-width: 0;
}

.option-name {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
  margin-bottom: 3px;
}

.option-desc {
  display: block;
  font-size: 12px;
  color: #888;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.option-check {
  color: #667eea;
  font-size: 18px;
  flex-shrink: 0;
}

.custom-rules {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.custom-rule-item {
  display: flex;
  gap: 10px;
  align-items: center;
}

.form-input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 13px;
  transition: all 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.btn-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: #fff0f0;
  border: none;
  border-radius: 8px;
  color: #ff4d4f;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.btn-remove:hover {
  background: #ffe0e0;
}

.btn-add-rule {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px;
  background: #f8f9fa;
  border: 2px dashed #e0e0e0;
  border-radius: 8px;
  color: #666;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-add-rule:hover {
  background: #f0f2ff;
  border-color: #667eea;
  color: #667eea;
}

@media (max-width: 1024px) {
  .preferences-page {
    height: auto;
    min-height: 100vh;
  }
  
  .preferences-content {
    grid-template-columns: 1fr;
    margin-bottom: 16px;
  }
  
  .preferences-column {
    overflow: visible;
    padding-right: 0;
  }
  
  .custom-rules-section {
    margin-top: 0;
  }
}

@media (max-width: 768px) {
  .preferences-page {
    padding: 16px;
  }
  
  .preferences-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }
  
  .header-left {
    width: 100%;
  }
  
  .btn-save {
    width: 100%;
    justify-content: center;
  }
}
</style>