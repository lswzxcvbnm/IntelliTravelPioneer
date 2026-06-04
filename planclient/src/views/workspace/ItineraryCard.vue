<template>
  <div class="ws-inline-itinerary">
    <div class="ws-ir-card" @click="toggleExpand">
      <span class="ws-ir-icon">📋</span>
      <span class="ws-ir-title">{{ itineraryData?.title || '行程方案' }}</span>
      <span class="ws-ir-badge">{{ itineraryData?.days }}天</span>
      <span class="ws-ir-badge ws-ir-budget">¥{{ itineraryData?.cost }}</span>
      <span class="ws-ir-toggle">{{ isExpanded ? '▲' : '▼' }}</span>
    </div>
    <transition name="ir-expand">
      <div v-if="isExpanded" class="ws-ir-body">
        <div
          v-for="day in itineraryData?.dailyPlans"
          :key="day.dayNumber"
          class="ws-ir-day"
        >
          <div class="ws-ir-day-header">
            <span class="ws-ir-day-num">{{ day.dayNumber }}</span>
            <span class="ws-ir-day-label">Day {{ day.dayNumber }}</span>
            <span class="ws-ir-day-count">{{ day.items.length }} 个地点</span>
          </div>
          <div class="ws-ir-items">
            <div
              v-for="(item, index) in day.items"
              :key="index"
              class="ws-ir-item"
              :class="{ expanded: isItemExpanded(day.dayNumber, index) }"
            >
              <div class="ws-ir-item-header" @click.stop="toggleItemExpand(day.dayNumber, index)">
                <span class="ws-ir-item-icon">{{ typeIconMap[item.type] || '📍' }}</span>
                <span class="ws-ir-item-name">{{ item.name }}</span>
                <span class="ws-ir-item-type">{{ item.type }}</span>
                <span class="ws-ir-item-toggle">{{ isItemExpanded(day.dayNumber, index) ? '▲' : '▼' }}</span>
              </div>
              <transition name="ir-item-expand">
                <div v-if="isItemExpanded(day.dayNumber, index)" class="ws-ir-item-detail">
                  <div v-if="item.description && item.description !== '暂无描述'" class="ws-ir-detail-row">
                    <span class="ws-ir-detail-icon">📝</span>
                    <span class="ws-ir-detail-text">{{ item.description }}</span>
                  </div>
                  <div v-if="item.location?.name" class="ws-ir-detail-row">
                    <span class="ws-ir-detail-icon">📍</span>
                    <span class="ws-ir-detail-text">{{ item.location.name }}</span>
                  </div>
                  <div v-if="hasValidCoords(item.location)" class="ws-ir-detail-row">
                    <a
                      class="ws-ir-map-link"
                      :href="`https://uri.amap.com/marker?position=${item.location?.lng},${item.location?.lat}`"
                      target="_blank"
                      rel="noopener"
                      @click.stop
                    >
                      🗺️ 在地图中查看
                    </a>
                  </div>
                </div>
              </transition>
            </div>
          </div>
        </div>
        <button class="ws-ir-open-canvas" @click.stop="$emit('openCanvas')">
          📋 在画布中打开
        </button>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

interface ItineraryDay {
  dayNumber: number
  items: Array<{
    name: string
    type: string
    description?: string
    location?: { lat: number; lng: number; name?: string }
  }>
}

interface ItineraryData {
  title: string
  days: number
  cost: number
  totalItems: number
  dailyPlans: ItineraryDay[]
}

const props = defineProps<{
  toolCalls: string
}>()

const emit = defineEmits<{
  openCanvas: []
}>()

const isExpanded = ref(false)
const expandedItems = ref<Set<string>>(new Set())

const typeIconMap: Record<string, string> = {
  '景点': '🏛',
  '餐饮': '🍜',
  '住宿': '🏨',
  '休闲': '🎭',
}

const itineraryData = computed<ItineraryData | null>(() => {
  try {
    const plan = JSON.parse(props.toolCalls)
    if (!plan || typeof plan !== 'object' || !plan.dailyPlans) return null
    return {
      title: plan.title || '未命名行程',
      days: plan.dailyPlans?.length || 0,
      cost: plan.estimatedCost || 0,
      totalItems: plan.dailyPlans?.reduce((s: number, d: any) => s + (d.items?.length || 0), 0) || 0,
      dailyPlans: (plan.dailyPlans || []).map((d: any) => ({
        dayNumber: d.dayNumber,
        items: (d.items || []).map((i: any) => ({
          name: i.name || '-',
          type: i.type || '-',
          description: i.description,
          location: i.location,
        })),
      })),
    }
  } catch {
    return null
  }
})

function toggleExpand() {
  isExpanded.value = !isExpanded.value
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

function hasValidCoords(loc?: { lat: number; lng: number }): boolean {
  return !!(loc && loc.lat !== 0 && loc.lng !== 0)
}
</script>

<style scoped>
.ws-inline-itinerary { margin-top: 10px; padding-top: 10px; border-top: 1px solid rgba(51, 133, 255, 0.08); }

.ws-ir-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: linear-gradient(135deg, #EEF4FF 0%, #F5F9FF 100%);
  border: 1px solid rgba(51, 133, 255, 0.12);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-ir-card:hover { border-color: rgba(51, 133, 255, 0.25); box-shadow: 0 2px 8px rgba(51, 133, 255, 0.06); }
.ws-ir-icon { font-size: 18px; }
.ws-ir-title { font-weight: 600; font-size: 13px; color: #1A6FFF; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ws-ir-badge { font-size: 11px; padding: 2px 8px; border-radius: 8px; background: rgba(51, 133, 255, 0.1); color: #3385FF; font-weight: 500; }
.ws-ir-budget { background: rgba(34, 197, 94, 0.1); color: #16A34A; }
.ws-ir-toggle { font-size: 10px; color: #94A3B8; margin-left: auto; }

.ws-ir-body {
  padding: 0 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ws-ir-day {
  background: #fff;
  border: 1px solid rgba(51, 133, 255, 0.06);
  border-radius: 10px;
  overflow: hidden;
}

.ws-ir-day-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(51, 133, 255, 0.03);
  border-bottom: 1px solid rgba(51, 133, 255, 0.05);
}
.ws-ir-day-num {
  width: 22px; height: 22px; border-radius: 50%;
  background: linear-gradient(135deg, #3385FF, #1A6FFF);
  color: #fff; font-size: 11px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.ws-ir-day-label { font-size: 12px; font-weight: 600; color: #1E3A5F; }
.ws-ir-day-count { font-size: 11px; color: #94A3B8; margin-left: auto; }

.ws-ir-items { padding: 8px 12px; display: flex; flex-direction: column; gap: 4px; }

.ws-ir-item {
  border-radius: 8px;
  border: 1px solid rgba(51, 133, 255, 0.04);
  overflow: hidden;
  transition: all 0.15s;
}
.ws-ir-item.expanded {
  border-color: rgba(51, 133, 255, 0.12);
  background: #FAFBFC;
}

.ws-ir-item-header {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 8px;
  font-size: 12px; color: #5B7FA5; line-height: 1.5;
  cursor: pointer;
  transition: background 0.1s;
}
.ws-ir-item-header:hover {
  background: rgba(51, 133, 255, 0.02);
}

.ws-ir-item-icon { font-size: 14px; flex-shrink: 0; }
.ws-ir-item-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; color: #1E3A5F; }
.ws-ir-item-type { font-size: 10px; padding: 1px 6px; border-radius: 6px; background: #F1F5F9; color: #7A8FA6; flex-shrink: 0; }
.ws-ir-item-toggle { font-size: 8px; color: #94A3B8; flex-shrink: 0; }

.ws-ir-item-detail {
  padding: 4px 8px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-top: 1px solid rgba(51, 133, 255, 0.04);
}

.ws-ir-detail-row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 11px;
  line-height: 1.5;
}
.ws-ir-detail-icon { flex-shrink: 0; font-size: 12px; }
.ws-ir-detail-text { color: #5B7FA5; word-break: break-word; }

.ws-ir-map-link {
  font-size: 11px;
  color: #3385FF;
  text-decoration: none;
  font-weight: 500;
}
.ws-ir-map-link:hover { text-decoration: underline; }

.ir-item-expand-enter-active,
.ir-item-expand-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}
.ir-item-expand-enter-from,
.ir-item-expand-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.ws-ir-open-canvas {
  margin: 8px 14px 12px;
  width: calc(100% - 28px);
  padding: 8px;
  border: 1.5px dashed rgba(51, 133, 255, 0.25);
  border-radius: 8px;
  background: transparent;
  color: #3385FF;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.ws-ir-open-canvas:hover { background: rgba(51, 133, 255, 0.04); border-style: solid; }

.ir-expand-enter-active,
.ir-expand-leave-active { transition: all 0.25s ease; overflow: hidden; }
.ir-expand-enter-from,
.ir-expand-leave-to { opacity: 0; max-height: 0; padding-top: 0; padding-bottom: 0; margin-top: 0; margin-bottom: 0; }
</style>