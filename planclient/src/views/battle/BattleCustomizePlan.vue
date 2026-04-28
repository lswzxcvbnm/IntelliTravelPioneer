<template>
  <div class="custom-page">
    <header class="top-bar">
      <button type="button" class="back-btn" @click="goBack">返回</button>
      <h1>{{ isEditMode ? '编辑行程计划' : '创建行程计划' }}</h1>
    </header>

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
              <button type="button" class="step-btn" @click="request.days = Math.max(1, request.days - 1)">−</button>
              <span class="step-val">{{ request.days }}</span>
              <button type="button" class="step-btn" @click="request.days = Math.min(30, request.days + 1)">+</button>
            </div>
          </label>

          <label class="input-card half">
            <span class="label-text">💰 预算</span>
            <div class="segment-group">
              <div
                v-for="opt in [{l:'经济',v:'low'},{l:'舒适',v:'medium'},{l:'豪华',v:'high'}]"
                :key="opt.v"
                class="segment-item"
                :class="{active: request.budgetLevel === opt.v}"
                @click="request.budgetLevel = opt.v"
              >{{ opt.l }}</div>
            </div>
          </label>
        </div>

        <div class="input-card full-width">
          <span class="label-text">✨ 兴趣偏好</span>
          <div class="chips-container">
            <button
              v-for="item in interestOptions"
              :key="item.value"
              type="button"
              class="chip-pill"
              :class="[{ active: request.interests.includes(item.value) }, `chip-${item.value}`]"
              @click="toggleInterest(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="input-card input-card-entry">
          <span class="label-text">✏️ 补充说明</span>
          <textarea
            v-model.trim="request.additionalNotes"
            rows="3"
            placeholder="例如：希望每天傍晚安排轻松步行，避开高峰景点"
          />
        </div>
      </div>

      <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>

      <div v-if="isLoadingGenerate" class="generating-logs-card">
        <div class="log-header">
          <div class="loader-spinner"></div>
          <span>AI 正在为您规划行程...</span>
        </div>
        <div class="log-preview">
          {{ generatingLogs.length > 0 ? generatingLogs[generatingLogs.length - 1] : '初始化中...' }}
        </div>
        <details class="log-details" @toggle="handleLogToggle">
          <summary>{{ isLogOpen ? '收起详细思考过程' : '展开查看详细思考过程' }}</summary>
          <div class="log-content">
            <p v-for="(log, idx) in generatingLogs" :key="idx" class="log-item">> {{ log }}</p>
          </div>
        </details>
      </div>

      <div class="sticky-actions">
        <button type="button" class="secondary" @click="jumpToExisting" v-if="isEditMode">加载已有</button>
        <button type="button" class="primary" @click="handleGenerate" :disabled="isLoadingGenerate">
          {{ isLoadingGenerate ? '正在生成...' : '✨ 生成 AI 方案' }}
        </button>
      </div>
      <div style="height: 80px;"></div>
    </section>

    <section class="card" v-if="step === 2">
      <h2>方案编辑</h2>
      <p class="hint">你可以自行增添，删除或调整行程，或者将你的想法告诉AI，让它帮你完善行程。</p>

      <div v-if="!editablePlan" class="empty">尚未生成或加载方案。</div>

      <template v-else>
        <div class="ai-refine-box">
          <label>
            <strong class="ai-refine-title">AI 行程完善</strong>
            <textarea
              v-model="refinePrompt"
              rows="3"
              placeholder="告诉AI你想如何修改，例如：'把第一天的景点换成室内的'、'晚餐都安排在酒店附近'"
            ></textarea>
          </label>
          <div v-if="isRefining" class="generating-logs-card">
            <div class="log-header">
              <div class="loader-spinner"></div>
              <span>AI 正在思考修改方案...</span>
            </div>
            <div class="log-content" style="max-height: 150px;">
              <p v-for="(log, idx) in refineLogs" :key="idx" class="log-item">> {{ log }}</p>
            </div>
          </div>
          <div class="actions ai-refine-actions">
            <button type="button" class="primary mini" @click="handleAiRefine" :disabled="isRefining || !refinePrompt.trim()">
              {{ isRefining ? 'AI 修改中...' : '发送给 AI 进行修改' }}
            </button>
          </div>
        </div>

        <label>
          计划标题
          <input v-model.trim="editablePlan.title" placeholder="请输入计划标题" />
        </label>

        <div class="form-grid compact">
          <label>
            预计花费
            <input v-model.number="editablePlan.estimatedCost" type="number" min="0" />
          </label>
          <label>
            总天数
            <input v-model.number="editablePlan.totalDays" type="number" min="1" max="30" />
          </label>
        </div>

        <div class="timeline-view">
          <div
            class="day-section"
            v-for="day in editablePlan.dailyPlans"
            :key="day.dayNumber"
            @dragover.prevent
            @drop="onDayDrop(day.dayNumber)"
          >
            <div class="day-sticky-header">
              <div class="day-badge-pill">
                <span class="day-num">DAY {{ day.dayNumber }}</span>
                <span class="day-dots"></span>
              </div>
              <button type="button" class="icon-btn-add" @click="addItem(day.dayNumber)">
                <span style="font-size: 18px; line-height: 1;">+</span>
              </button>
            </div>

            <div class="timeline-list">
              <div
                class="timeline-row"
                v-for="(item, idx) in day.items"
                :key="`${day.dayNumber}-${idx}`"
                draggable="true"
                @dragstart="onDragStart(day.dayNumber, idx)"
                @dragover.prevent
                @drop.stop="onDrop(day.dayNumber, idx)"
              >
                <div class="timeline-thread">
                  <div class="thread-line"></div>
                  <div class="node-circle" :class="`bg-${getTypeClass(item.type)}`">
                    {{ getTypeIcon(item.type) }}
                  </div>
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
                      <button
                        class="text-btn"
                        @click="toggleItemEditor(day.dayNumber, idx)"
                      >
                        {{ isItemEditing(day.dayNumber, idx) ? '收起' : '编辑' }}
                      </button>
                      <button class="text-btn danger" @click="removeItem(day.dayNumber, idx)">删除</button>
                    </div>

                    <div v-if="isItemEditing(day.dayNumber, idx)" class="embedded-editor">
                      <div class="field-row">
                        <select v-model="item.type" class="mini-select">
                          <option v-for="type in routeTypeOptions" :key="type" :value="type">{{ type }}</option>
                        </select>
                        <input v-model.trim="item.name" placeholder="名称" class="mini-input" />
                      </div>
                      <textarea v-model.trim="item.description" rows="2" placeholder="描述..." class="mini-textarea" />
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="day.items.length === 0" class="empty-day-placeholder">
                拖拽或是点击右上角+号添加行程
              </div>
            </div>
          </div>
        </div>

        <div class="sticky-actions">
          <button type="button" class="secondary" @click="step = 1">返回上一页</button>
          <button
            type="button"
            class="primary"
            @click="savePlan"
            :disabled="!editablePlan || isSaving"
          >
            {{ isSaving ? '保存中...' : isEditMode ? '更新方案' : '保存方案' }}
          </button>
        </div>
        <div style="height: 80px;"></div>
      </template>
    </section>

    <section class="card" v-if="step === 3">
      <h2>行程详情预览</h2>
      <p class="hint">方案已保存，你可以继续编辑或返回列表查看。</p>

      <div v-if="!editablePlan" class="empty">暂无可预览的方案。</div>

      <template v-else>
        <div class="saved-preview">
          <div class="saved-preview-header">
            <h3>行程详情预览</h3>
            <span class="saved-tag">已保存</span>
          </div>
          <div class="saved-overview">
            <p><strong>标题：</strong>{{ editablePlan.title || '(未命名计划)' }}</p>
            <p><strong>总天数：</strong>{{ editablePlan.totalDays }} 天</p>
            <p><strong>预算：</strong>{{ formatCurrency(editablePlan.estimatedCost) }}</p>
            <p><strong>路线项：</strong>{{ routeItemCount }} 个</p>
          </div>

          <div class="preview-days">
            <div class="preview-day" v-for="day in editablePlan.dailyPlans" :key="`preview-${day.dayNumber}`">
              <p class="preview-day-title">第 {{ day.dayNumber }} 天</p>
              <ul>
                <li v-for="(item, index) in day.items" :key="`preview-item-${day.dayNumber}-${index}`">
                  <span>{{ getTypeIcon(item.type) }}</span>
                  <span>{{ item.name }}</span>
                </li>
              </ul>
            </div>
          </div>

          <div class="sticky-actions">
            <button type="button" class="secondary" @click="continueEditing">继续编辑</button>
            <button type="button" class="primary" @click="goBackToList">返回列表</button>
          </div>
          <div style="height: 80px;"></div>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  generateItineraryStream,
  getItineraryDetail,
  saveItinerary,
  updateItinerary
} from '../../api/itinerary'
import type { ItineraryPlan, ItineraryRequest } from '../../api/itinerary'
import { goBackByAppRule } from '../../composables/useBackHandler'

const router = useRouter()
const route = useRoute()
const goBack = () => goBackByAppRule(router)

const step = ref(1)
const isLoadingGenerate = ref(false)
const generatingLogs = ref<string[]>([])
const isLogOpen = ref(false)
const handleLogToggle = (event: Event) => {
  isLogOpen.value = (event.target as HTMLDetailsElement).open
}
const isSaving = ref(false)
const errorMessage = ref('')
const editablePlan = ref<ItineraryPlan | null>(null)
const savedItineraryId = ref('')

const refinePrompt = ref('')
const isRefining = ref(false)
const refineLogs = ref<string[]>([])

const itineraryId = computed(() => String(route.query.itineraryId || ''))
const isEditMode = computed(() => itineraryId.value.length > 0)

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
  maps_weather: '正在查询天气情况...'
}

const getLogMessage = (msg: string) => {
  const invokeMatch = msg.match(/Invoking tool: (\w+)/)
  if (invokeMatch && invokeMatch[1]) {
    const toolName = invokeMatch[1]
    return toolNameMapping[toolName] || msg
  }

  const completeMatch = msg.match(/Tool (\w+) completed/)
  if (completeMatch && completeMatch[1]) {
    const toolName = completeMatch[1]
    if (toolNameMapping[toolName]) {
      return toolNameMapping[toolName].replace('正在', '已完成').replace('...', '。')
    }
  }

  return toolNameMapping[msg] || msg
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

const normalizeRouteType = (value: string) => {
  return routeTypeOptions.includes(value) ? value : DEFAULT_ROUTE_TYPE
}

const request = reactive<ItineraryRequest>({
  destination: Array.isArray(route.query.destination) ? String(route.query.destination[0] || '') : String(route.query.destination || ''),
  days: 3,
  interests: [],
  budgetLevel: 'medium',
  additionalNotes: ''
})

const draggingItem = ref<{ dayNumber: number; index: number } | null>(null)
const editingItemKey = ref('')

const routeItemCount = computed(() => {
  if (!editablePlan.value) return 0
  return editablePlan.value.dailyPlans.reduce((sum, day) => sum + day.items.length, 0)
})

const formatCurrency = (value: number) => `￥${value.toLocaleString('zh-CN')}`

const toggleInterest = (value: string) => {
  const exists = request.interests.includes(value)
  if (exists) {
    request.interests = request.interests.filter((item) => item !== value)
    return
  }
  request.interests = [...request.interests, value]
}

const validateRequest = () => {
  if (!request.destination || !request.destination.trim()) return '想去哪里探索？请先输入你的目的地。'
  if (request.days < 1 || request.days > 30) return '出行天数需在 1-30 之间。'
  return ''
}

const normalizePlan = (plan: ItineraryPlan): ItineraryPlan => {
  const dailyPlans = plan.dailyPlans.map((day, index) => ({
    dayNumber: day.dayNumber || index + 1,
    items: day.items.map((item) => ({
      type: normalizeRouteType(item.type),
      name: item.name || '未命名路线项',
      description: item.description || '暂无描述',
      location: item.location || { lat: 0, lng: 0, name: '' }
    }))
  }))

  return {
    title: plan.title || `${request.destination || '我的'}行程计划`,
    estimatedCost: Number(plan.estimatedCost || 0),
    totalDays: Number(plan.totalDays || dailyPlans.length || 1),
    dailyPlans
  }
}

const handleGenerate = async () => {
  errorMessage.value = ''
  generatingLogs.value = []

  const validationError = validateRequest()
  if (validationError) {
    errorMessage.value = validationError
    return
  }

  isLoadingGenerate.value = true

  try {
    await generateItineraryStream(
      { ...request },
      (msg) => {
        generatingLogs.value.push(getLogMessage(msg))
      },
      (plan) => {
        editablePlan.value = normalizePlan(plan)
        step.value = 2
        isLoadingGenerate.value = false
      },
      (error) => {
        console.error('生成行程报错:', error)
        errorMessage.value = error.message || '生成报错，请稍后重试。'
        isLoadingGenerate.value = false
      }
    )
  } catch (error) {
    console.error('发起请求失败:', error)
    errorMessage.value = '生成失败，请稍后重试。'
    isLoadingGenerate.value = false
  }
}

const handleAiRefine = async () => {
  if (!refinePrompt.value.trim() || !editablePlan.value) return
  isRefining.value = true
  refineLogs.value = []

  const refineRequest: ItineraryRequest = {
    ...request,
    additionalNotes: `【注意事项：用户需要修改这份已有计划，请尽量保持没被修改的部分，只修改用户明确指出的内容，并且输出完整的行程计划格式】\n当前已有计划：\n${JSON.stringify(editablePlan.value)}\n\n用户修改建议：\n${refinePrompt.value}\n\n${request.additionalNotes ? '原补充说明：' + request.additionalNotes : ''}`
  }

  try {
    await generateItineraryStream(
      refineRequest,
      (msg) => {
        refineLogs.value.push(getLogMessage(msg))
      },
      (plan) => {
        editablePlan.value = normalizePlan(plan)
        isRefining.value = false
        refinePrompt.value = ''
      },
      (error) => {
        console.error('AI修改行程报错:', error)
        ElMessageBox.alert(error.message || '修改报错，请稍后重试。', 'AI修改失败', { type: 'error' })
        isRefining.value = false
      }
    )
  } catch (error: any) {
    console.error('发起AI修改请求失败:', error)
    ElMessageBox.alert(error.message || '修改失败，请稍后重试。', 'AI修改失败', { type: 'error' })
    isRefining.value = false
  }
}

const addItem = (dayNumber: number) => {
  if (!editablePlan.value) return
  const day = editablePlan.value.dailyPlans.find((entry) => entry.dayNumber === dayNumber)
  if (!day) return
  day.items.push({
    type: DEFAULT_ROUTE_TYPE,
    name: '新路线项',
    description: '请补充该路线项描述',
    location: { lat: 0, lng: 0 , name: '' }
  })
}

const removeItem = (dayNumber: number, index: number) => {
  if (!editablePlan.value) return
  const day = editablePlan.value.dailyPlans.find((entry) => entry.dayNumber === dayNumber)
  if (!day) return
  day.items.splice(index, 1)

  if (editingItemKey.value === `${dayNumber}-${index}`) {
    editingItemKey.value = ''
  }
}

const toggleItemEditor = (dayNumber: number, index: number) => {
  const key = `${dayNumber}-${index}`
  editingItemKey.value = editingItemKey.value === key ? '' : key
}

const isItemEditing = (dayNumber: number, index: number) => {
  return editingItemKey.value === `${dayNumber}-${index}`
}

const getTypeIcon = (type: string) => {
  switch (type) {
    case '景点': return '🗺'
    case '美食': return '🍜'
    case '交通': return '🚌'
    case '住宿': return '🏨'
    case '休闲': return '🌿'
    case '购物': return '🛍'
    default: return '📍'
  }
}

const getTypeClass = (type: string) => {
  switch (type) {
    case '景点': return 'spot'
    case '美食': return 'food'
    case '交通': return 'traffic'
    case '住宿': return 'hotel'
    case '休闲': return 'relax'
    case '购物': return 'shopping'
    default: return 'default'
  }
}

const onDragStart = (dayNumber: number, index: number) => {
  draggingItem.value = { dayNumber, index }
}

const onDrop = (targetDayNumber: number, targetIndex: number) => {
  if (!editablePlan.value || !draggingItem.value) return

  const source = draggingItem.value
  const sourceDayNumber = source.dayNumber
  const sourceIndex = source.index

  const sourceDay = editablePlan.value.dailyPlans.find((entry) => entry.dayNumber === sourceDayNumber)
  const targetDay = editablePlan.value.dailyPlans.find((entry) => entry.dayNumber === targetDayNumber)

  if (!sourceDay || !targetDay) {
    draggingItem.value = null
    return
  }

  const moved = sourceDay.items[sourceIndex]
  if (!moved) {
    draggingItem.value = null
    return
  }

  if (sourceDayNumber === targetDayNumber) {
    if (sourceIndex === targetIndex) {
      draggingItem.value = null
      return
    }
    sourceDay.items.splice(sourceIndex, 1)
    const insertIndex = sourceIndex < targetIndex ? targetIndex - 1 : targetIndex
    sourceDay.items.splice(insertIndex, 0, moved)
  } else {
    sourceDay.items.splice(sourceIndex, 1)
    targetDay.items.splice(targetIndex, 0, moved)
  }

  draggingItem.value = null
}

const onDayDrop = (targetDayNumber: number) => {
  if (!editablePlan.value || !draggingItem.value) return

  const source = draggingItem.value
  const sourceDayNumber = source.dayNumber
  const sourceIndex = source.index

  const sourceDay = editablePlan.value.dailyPlans.find((entry) => entry.dayNumber === sourceDayNumber)
  const targetDay = editablePlan.value.dailyPlans.find((entry) => entry.dayNumber === targetDayNumber)

  if (!sourceDay || !targetDay) {
    draggingItem.value = null
    return
  }

  const moved = sourceDay.items[sourceIndex]
  if (!moved) {
    draggingItem.value = null
    return
  }

  if (sourceDayNumber === targetDayNumber) {
    if (sourceIndex === sourceDay.items.length - 1) {
       draggingItem.value = null
       return
    }
    sourceDay.items.splice(sourceIndex, 1)
    sourceDay.items.push(moved)
  } else {
    sourceDay.items.splice(sourceIndex, 1)
    targetDay.items.push(moved)
  }

  draggingItem.value = null
}

const jumpToExisting = () => {
  if (!isEditMode.value) return
  loadDetailForEdit()
}

const loadDetailForEdit = async () => {
  if (!itineraryId.value) return

  isLoadingGenerate.value = true
  errorMessage.value = ''

  try {
    const response = await getItineraryDetail(itineraryId.value)
    editablePlan.value = normalizePlan(response.data.data.plan)
    savedItineraryId.value = response.data.data.id
    step.value = 2
  } catch (error) {
    console.error('加载行程详情失败:', error)
    errorMessage.value = '加载已有计划失败，请稍后重试。'
  } finally {
    isLoadingGenerate.value = false
  }
}

const validatePlan = () => {
  if (!editablePlan.value) return '请先生成或加载行程方案。'
  if (!editablePlan.value.title.trim()) return '计划标题不能为空。'
  if (editablePlan.value.totalDays < 1) return '总天数必须大于 0。'
  return ''
}

const savePlan = async () => {
  errorMessage.value = ''

  const validationError = validatePlan()
  if (validationError) {
    errorMessage.value = validationError
    return
  }

  if (!editablePlan.value) return
  isSaving.value = true
  const destinationCity = request.destination.trim()

  try {
    if (isEditMode.value) {
      await updateItinerary(itineraryId.value, {
        ...editablePlan.value,
        destination: destinationCity
      })
      savedItineraryId.value = itineraryId.value
      step.value = 3
    } else {
      const response = await saveItinerary({
        title: editablePlan.value.title,
        estimatedCost: editablePlan.value.estimatedCost,
        totalDays: editablePlan.value.totalDays,
        destination: destinationCity,
        dailyPlans: editablePlan.value.dailyPlans
      })
      savedItineraryId.value = response.data.data
      step.value = 3
    }
  } catch (error) {
    console.error('保存行程失败:', error)
    errorMessage.value = '保存失败，请稍后重试。'
  } finally {
    isSaving.value = false
  }
}

const continueEditing = () => {
  step.value = 2
}

const goBackToList = () => {
  router.push('/battle')
}

onMounted(async () => {
  if (route.query.destination) {
    request.destination = String(route.query.destination)
  }

  if (itineraryId.value) {
    await loadDetailForEdit()
  }
})
</script>

<style scoped>
.custom-page {
  --primary-color: #3385FF;
  --primary-hover: #1A6FFF;
  --primary-light: #EBF5FF;
  --secondary-color: #F5FAFF;
  --secondary-hover: #DBEAFF;
  --danger-color: #E53E3E;
  --bg-color: #F5FAFF;
  --card-bg: #ffffff;
  --input-bg: #F5FAFF;
  --text-main: #1E3A5F;
  --text-muted: #6B8CBE;
  --border-color: rgba(51, 133, 255, 0.15);

  --radius-lg: 24px;
  --radius-md: 16px;
  --radius-sm: 12px;

  --shadow-sm: 0 1px 3px rgba(51, 133, 255, 0.05);
  --shadow-md: 0 4px 6px -1px rgba(51, 133, 255, 0.05), 0 2px 4px -1px rgba(51, 133, 255, 0.03);
  --shadow-lg: 0 10px 15px -3px rgba(51, 133, 255, 0.05), 0 4px 6px -2px rgba(51, 133, 255, 0.025);

  min-height: 100dvh;
  padding: 16px 16px calc(80px + env(safe-area-inset-bottom, 0px));
  background: linear-gradient(180deg, #FFFFFF 0%, #EBF5FF 50%, #F5FAFF 100%);
  color: var(--text-main);
  font-family: ui-sans-serif, system-ui, sans-serif;
}

.sticky-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px;
  padding-bottom: calc(16px + env(safe-area-inset-bottom, 0px));
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border-top: 1px solid rgba(0,0,0,0.05);
  z-index: 100;
  display: flex;
  gap: 12px;
}

.sticky-actions button {
  flex: 1;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  position: relative;
}

.top-bar h1 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-main);
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.back-btn {
  border-radius: var(--radius-sm);
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  border: 1px solid var(--border-color);
  background: var(--card-bg);
  color: var(--text-main);
  cursor: pointer;
  transition: var(--transition);
  box-shadow: var(--shadow-sm);
  z-index: 10;
}

.back-btn:hover {
  background: var(--secondary-color);
}

.card {
  border-radius: var(--radius-lg);
  border: 1px solid rgba(0, 0, 0, 0.02);
  background: var(--card-bg);
  padding: 24px;
  box-shadow: var(--shadow-md);
  margin-bottom: 20px;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.card h2 {
  margin: 0 0 20px 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-main);
}

.hint {
  margin: 0 0 20px 0;
  font-size: 14px;
  color: var(--text-muted);
  line-height: 1.6;
  background: var(--primary-light);
  padding: 12px 16px;
  border-radius: var(--radius-md);
  border-left: 4px solid var(--primary-color);
}

.form-cols {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 24px;
}

.row-group {
  display: flex;
  gap: 12px;
}

.input-card {
  background: var(--card-bg);
  border-radius: var(--radius-md);
  padding: 16px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  flex: 1;
}

.input-card.half {
  flex: 1;
}

.label-text {
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.large-input {
  border: none;
  background: transparent;
  padding: 0;
  min-height: auto;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
  width: 100%;
}
.large-input:focus {
  box-shadow: none;
}

.input-card-entry .large-input {
  display: block;
  box-sizing: border-box;
  min-height: 36px;
  padding: 7px 0;
  line-height: 1.5;
  caret-color: var(--text-main);
}

.stepper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  background: var(--input-bg);
  border-radius: var(--radius-sm);
  padding: 4px;
}

.step-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--card-bg);
  border: 1px solid rgba(0,0,0,0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--primary-color);
  font-weight: bold;
  padding: 0;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.step-val {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.segment-group {
  display: flex;
  background: var(--input-bg);
  border-radius: var(--radius-sm);
  padding: 4px;
  width: 100%;
  gap: 4px;
}

.segment-item {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.segment-item.active {
  background: var(--card-bg);
  color: var(--primary-color);
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.form-grid.compact {
  margin-top: 20px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

input,
textarea,
select {
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  font-size: 14px;
  color: var(--text-main);
  background: var(--input-bg);
  transition: var(--transition);
  min-height: 48px;
  font-family: inherit;
}

::placeholder {
  color: #94a3b8;
  opacity: 1;
}

input:focus,
textarea:focus,
select:focus {
  outline: none;
  background: var(--card-bg);
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.chips-container {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chip-pill {
  border-radius: 9999px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  border: 1px solid transparent;
  background: var(--input-bg);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.3, 0.7, 0.4, 0.1);
  flex: 1 1 auto;
  text-align: center;
  white-space: nowrap;
}

.chip-pill:hover {
  background: #e2e8f0;
}

.chip-pill.active {
  background: var(--primary-color);
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.chip-history.active { background: #d97706; box-shadow: 0 4px 12px rgba(217, 119, 6, 0.3); }
.chip-food.active { background: #ea580c; box-shadow: 0 4px 12px rgba(234, 88, 12, 0.3); }
.chip-nature.active { background: #16a34a; box-shadow: 0 4px 12px rgba(22, 163, 74, 0.3); }
.chip-citywalk.active { background: #0284c7; box-shadow: 0 4px 12px rgba(2, 132, 199, 0.3); }
.chip-night.active { background: #7c3aed; box-shadow: 0 4px 12px rgba(124, 58, 237, 0.3); }

textarea {
  width: 100%;
  border: none;
  background: transparent;
  padding: 0;
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
  color: var(--text-main);
  line-height: 1.6;
}

.input-card-entry textarea {
  display: block;
  box-sizing: border-box;
  padding: 8px 4px;
  caret-color: var(--text-main);
}

.input-card:focus-within {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
}

.ai-refine-box {
  background: var(--secondary-color);
  border-radius: var(--radius-md);
  padding: 20px;
  margin-bottom: 28px;
  border: 1px solid var(--border-color);
}

.ai-refine-title {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.05rem;
  color: var(--text-main);
  font-weight: 600;
}

.ai-refine-title::before {
  content: '✨';
}

.ai-refine-actions {
  margin-top: 16px;
  margin-bottom: 0;
  display: flex;
  justify-content: flex-end;
}

.error-text {
  color: var(--danger-color);
  font-size: 14px;
  margin-top: 16px;
  background: var(--danger-light);
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  border-left: 4px solid var(--danger-color);
  font-weight: 500;
}

.generating-logs-card {
  padding: 20px;
  background: var(--primary-light);
  border-radius: var(--radius-md);
  margin-bottom: 24px;
  border: 1px solid rgba(99, 102, 241, 0.2);
}

.log-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 700;
  color: var(--primary-color);
  margin-bottom: 12px;
}

.loader-spinner {
  width: 20px;
  height: 20px;
  border: 3px solid var(--primary-color);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.log-preview {
  font-size: 14px;
  color: var(--text-main);
  margin-bottom: 16px;
  padding: 12px;
  background: rgba(255,255,255,0.6);
  border-radius: var(--radius-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-details {
  font-size: 12px;
  color: var(--text-muted);
}
.log-details summary {
  cursor: pointer;
  padding: 6px 12px;
  background: rgba(255,255,255,0.5);
  border-radius: 20px;
  display: inline-block;
  user-select: none;
  font-weight: 600;
  transition: all 0.2s;
}
.log-details summary:hover {
  background: #fff;
  color: var(--primary-color);
}
.log-details .log-content {
  margin-top: 12px;
  background: #1e293b;
  padding: 12px;
  border-radius: var(--radius-sm);
  max-height: 200px;
  overflow-y: auto;
  color: #fff;
  font-family: monospace;
}
.log-item {
  margin: 4px 0;
  line-height: 1.4;
}

.timeline-view {
  padding-bottom: 40px;
}

.day-section {
  position: relative;
  margin-bottom: 40px;
}

.day-sticky-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  background: transparent;
  pointer-events: none;
}

.day-badge-pill {
  background: var(--primary-color);
  color: #fff;
  padding: 6px 16px;
  border-radius: 20px;
  font-weight: 800;
  font-size: 14px;
  box-shadow: 0 4px 10px rgba(99, 102, 241, 0.3);
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn-add {
  background: var(--card-bg);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  font-weight: bold;
  box-shadow: var(--shadow-sm);
  pointer-events: auto;
  cursor: pointer;
}

.timeline-list {
  padding-left: 12px;
  border-left: 2px dashed rgba(99, 102, 241, 0.1);
  margin-left: 20px;
  padding-top: 10px;
  padding-bottom: 10px;
}

.timeline-row {
  position: relative;
  margin-bottom: 24px;
}

.timeline-row:last-child {
  margin-bottom: 0;
}

.timeline-thread {
  position: absolute;
  left: -32px;
  top: 0;
  width: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.node-circle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: var(--card-bg);
  border: 4px solid var(--bg-color);
  z-index: 10;
  box-shadow: var(--shadow-sm);
}

.bg-spot { background: #e0f2fe; color: #0284c7; }
.bg-food { background: #ffedd5; color: #ea580c; }
.bg-traffic { background: #f3e8ff; color: #7c3aed; }
.bg-hotel { background: #dbeafe; color: #2563eb; }
.bg-relax { background: #dcfce7; color: #16a34a; }
.bg-shopping { background: #fce7f3; color: #db2777; }
.bg-default { background: #f1f5f9; color: #475569; }

.timeline-card-wrap {
  margin-left: 20px;
}

.timeline-card {
  background: var(--card-bg);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid rgba(0,0,0,0.03);
  transition: all 0.2s;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.card-title {
  font-weight: 700;
  font-size: 16px;
  color: var(--text-main);
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: var(--text-muted);
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
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-color);
}

.text-btn {
  background: none;
  border: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--primary-color);
  padding: 4px 8px;
  cursor: pointer;
}

.text-btn.danger {
  color: var(--text-muted);
}

.text-btn.danger:hover {
  color: var(--danger-color);
}

.embedded-editor {
  margin-top: 12px;
  background: var(--secondary-color);
  padding: 12px;
  border-radius: var(--radius-sm);
  display: flex;
  flex-direction: column;
  gap: 8px;
  animation: fadeIn 0.3s ease-out;
}

.field-row {
  display: flex;
  gap: 8px;
}

.mini-select, .mini-input, .mini-textarea {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 14px;
  width: 100%;
  font-family: inherit;
  color: var(--text-main);
}

.mini-select {
  flex: 0 0 90px;
}

.mini-textarea {
  resize: none;
  min-height: 60px;
}

.empty-day-placeholder {
  font-size: 13px;
  color: var(--text-muted);
  text-align: center;
  padding: 20px;
  border: 2px dashed var(--border-color);
  border-radius: var(--radius-md);
  margin-left: 20px;
  background: rgba(255,255,255,0.4);
}

.drag-handle {
  color: #cbd5e1;
  padding: 4px;
  cursor: grab;
  font-size: 20px;
  transition: var(--transition);
  margin-left: 8px;
}

.empty {
  border: 2px dashed var(--border-color);
  border-radius: var(--radius-md);
  padding: 40px 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 15px;
  background: var(--secondary-color);
}

.primary,
.secondary,
.mini {
  border-radius: var(--radius-md);
  border: none;
  padding: 0 24px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.primary {
  background: var(--primary-color);
  color: #fff;
  flex: 1;
  min-height: 48px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.primary:hover:not(:disabled) {
  background: var(--primary-hover);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.4);
  transform: translateY(-1px);
}

.primary:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.primary:disabled {
  background: #93c5fd;
  cursor: not-allowed;
  box-shadow: none;
}

.secondary {
  background: var(--secondary-color);
  color: var(--text-main);
  min-height: 48px;
}

.secondary:hover {
  background: var(--secondary-hover);
  transform: translateY(-1px);
}

.secondary:active {
  transform: translateY(1px);
}

.mini {
  background: var(--secondary-color);
  color: var(--text-main);
  min-height: 40px;
  padding: 0 16px;
  font-size: 14px;
  border: 1px solid var(--border-color);
}

.mini:hover {
  background: var(--card-bg);
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.saved-preview {
  border: 1px solid var(--primary-light);
  border-radius: var(--radius-lg);
  padding: 24px;
  background: var(--card-bg);
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.08);
}

.saved-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}

.saved-preview-header h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-main);
}

.saved-tag {
  border-radius: 20px;
  background: #dcfce7;
  color: #166534;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.saved-tag::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  background: #16a34a;
  border-radius: 50%;
}

.saved-overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
  background: var(--secondary-color);
  padding: 16px;
  border-radius: var(--radius-md);
}

.saved-overview p {
  margin: 0;
  font-size: 15px;
  color: var(--text-muted);
}

.saved-overview strong {
  color: var(--text-main);
  font-weight: 600;
  margin-right: 4px;
}

.preview-days {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview-day {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 16px;
  transition: var(--transition);
}

.preview-day:hover {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-sm);
}

.preview-day-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
  color: var(--primary-color);
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-day ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-day li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--text-main);
  padding: 8px 12px;
  background: var(--secondary-color);
  border-radius: var(--radius-sm);
}
</style>
