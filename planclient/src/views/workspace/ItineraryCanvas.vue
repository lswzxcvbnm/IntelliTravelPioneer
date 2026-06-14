<template>
  <aside class="canvas" :class="{ 'canvas--empty': !editablePlan }">
    <!-- 头部 -->
    <div class="canvas-header">
      <h3 class="canvas-header-title">
        <span class="header-icon">🗺</span> 行程画布
      </h3>
    </div>

    <!-- 画布主体 -->
    <div class="canvas-body">
      <div v-if="!editablePlan" class="canvas-empty">
        <div class="empty-visual">
          <div class="empty-icon-ring">🗺</div>
          <div class="empty-timeline-preview">
            <div class="empty-day-node"></div>
            <div class="empty-day-line"></div>
            <div class="empty-day-node is-faded"></div>
            <div class="empty-day-line"></div>
            <div class="empty-day-node is-faded"></div>
          </div>
        </div>
        <h3 class="empty-title">行程画布</h3>
        <p class="empty-desc">在左侧与 AI 对话生成行程后，<br>这里将展示完整的行程时间线</p>
        <div class="empty-features">
          <div class="empty-feature">
            <span class="empty-feature-icon">📅</span>
            <span>按天分组展示</span>
          </div>
          <div class="empty-feature">
            <span class="empty-feature-icon">📝</span>
            <span>点击查看详情</span>
          </div>
          <div class="empty-feature">
            <span class="empty-feature-icon">🗺</span>
            <span>地图快速跳转</span>
          </div>
        </div>
      </div>

      <template v-else>
        <!-- 行程标题 -->
        <h2 class="canvas-title-text">{{ editablePlan.title }}</h2>

        <!-- 概览信息行 -->
        <div class="canvas-summary">
          <span class="summary-item">📅 {{ editablePlan.totalDays }} 天</span>
          <span class="summary-item">💰 预算 ¥{{ editablePlan.estimatedCost || 0 }}</span>
          <span class="summary-item">📍 {{ totalItems }} 个地点</span>
        </div>

        <!-- 时间线 -->
        <div class="canvas-timeline">
          <div
            v-for="day in editablePlan.dailyPlans"
            :key="day.dayNumber"
            class="day-section"
            @dragover.prevent
            @drop="handleDayDrop($event, day.dayNumber)"
          >
            <!-- 时间线节点 -->
            <div class="day-node">
              <span class="day-num">{{ day.dayNumber }}</span>
              <div class="day-line"></div>
            </div>

            <!-- 天数内容 -->
            <div class="day-body">
              <div class="day-header">
                <span class="day-label">Day {{ day.dayNumber }}</span>
                <span class="day-count">{{ day.items.length }} 项</span>
              </div>

              <div v-if="day.items.length > 0" class="day-items">
                <TransitionGroup name="item-list" tag="div" class="day-items-wrapper">
                  <div
                    v-for="(item, ii) in day.items"
                    :key="`${day.dayNumber}-${ii}`"
                    class="travel-item"
                    :class="{ 'travel-item--expanded': isExpanded(day.dayNumber, ii) }"
                    draggable="true"
                    @dragstart="$emit('drag-start', $event, day.dayNumber, ii)"
                    @dragover.prevent
                    @drop="handleItemDrop($event, day.dayNumber, ii)"
                  >
                    <div
                      class="travel-item-header"
                      @click="$emit('item-toggle', day.dayNumber, ii)"
                    >
                      <span class="travel-item-badge" :class="'badge--' + getTypeClass(item.type)">
                        <span class="badge-icon">{{ getTypeIcon(item.type) }}</span>
                        {{ item.type }}
                      </span>
                      <span class="travel-item-name">{{ item.name || '未命名' }}</span>
                      <span v-if="item.timeSlot" class="travel-item-time">{{ item.timeSlot }}</span>
                      <span class="travel-item-expand-hint">
                        {{ isExpanded(day.dayNumber, ii) ? '收起 ▴' : '详情 ▾' }}
                      </span>
                      <button
                        class="item-delete-btn"
                        title="删除此项"
                        @click.stop="$emit('item-delete', day.dayNumber, ii, item.name)"
                      >删除</button>
                    </div>

                    <Transition name="item-detail">
                      <div v-if="isExpanded(day.dayNumber, ii)" class="travel-item-detail">
                        <div v-if="item.description && item.description !== '暂无描述'" class="detail-row">
                          <span class="detail-label">📝 描述</span>
                          <span class="detail-value">{{ item.description }}</span>
                        </div>
                        <div v-if="item.location?.name" class="detail-row">
                          <span class="detail-label">📍 地点</span>
                          <span class="detail-value">{{ item.location.name }}</span>
                        </div>
                        <div v-if="hasValidCoords(item.location)" class="detail-row">
                          <span class="detail-label">🌐 坐标</span>
                          <code>{{ item.location!.lat.toFixed(6) }}, {{ item.location!.lng.toFixed(6) }}</code>
                        </div>
                        <a
                          v-if="hasValidCoords(item.location)"
                          class="detail-map-link"
                          :href="`https://uri.amap.com/marker?position=${item.location!.lng},${item.location!.lat}`"
                          target="_blank"
                          rel="noopener"
                        >🗺 在高德地图中查看</a>
                      </div>
                    </Transition>
                  </div>
                </TransitionGroup>
              </div>

              <div v-else class="day-empty">拖拽行程项到此处安排</div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import type { ItineraryPlan } from '../../api/itinerary'

const props = defineProps<{
  editablePlan: ItineraryPlan | null
  isSaving: boolean
  totalItems: number
  expandedItems: Set<string>
}>()

const emit = defineEmits<{
  save: []
  'item-toggle': [dayNum: number, idx: number]
  'item-delete': [dayNum: number, idx: number, itemName?: string]
  'drag-start': [event: DragEvent, dayNum: number, idx: number]
  'item-drop': [event: DragEvent, targetDay: number, targetIdx: number]
  'day-drop': [dayNum: number]
}>()

function handleItemDrop(e: DragEvent, dayNum: number, idx: number) {
  emit('item-drop', e, dayNum, idx)
}
function handleDayDrop(_e: DragEvent, dayNum: number) {
  emit('day-drop', dayNum)
}

function getTypeClass(t: string): string {
  const map: Record<string, string> = {
    '景点': 'spot', '餐饮': 'food', '交通': 'traffic',
    '住宿': 'hotel', '休闲': 'relax', '购物': 'shopping',
  }
  return map[t] || 'default'
}

function getTypeIcon(t: string): string {
  const map: Record<string, string> = {
    '景点': '🏛', '餐饮': '🍜', '交通': '🚇',
    '住宿': '🏨', '休闲': '☕', '购物': '🛍',
  }
  return map[t] || '📍'
}

function hasValidCoords(loc?: { lat: number; lng: number } | null): boolean {
  return !!(loc && loc.lat !== 0 && loc.lng !== 0)
}

function isExpanded(dayNum: number, idx: number): boolean {
  return props.expandedItems.has(`${dayNum}-${idx}`)
}
</script>

<style scoped>
/* ===== 整体画布 ===== */
.canvas {
  width: 560px;
  min-width: 480px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #FAFBFC;
  border-left: 1px solid #E5E9F0;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif;
}

/* ===== 头部 ===== */
.canvas-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #E5E9F0;
  flex-shrink: 0;
}
.canvas-header-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin: 0;
  display: flex;
  align-items: center;
}
.header-icon {
  margin-right: 6px;
  font-size: 16px;
}

/* ===== 画布主体 ===== */
.canvas-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ===== 空状态 ===== */
.canvas-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 20px;
  text-align: center;
}

/* 视觉区域 */
.empty-visual {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
}
.empty-icon-ring {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #F5F7FA;
  border: 2px solid #E4E7ED;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  z-index: 1;
}

/* 迷你时间线预览 */
.empty-timeline-preview {
  position: absolute;
  left: calc(50% + 42px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  opacity: 0.25;
  pointer-events: none;
}
.empty-day-node {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #3385FF;
  flex-shrink: 0;
}
.empty-day-node.is-faded {
  background: #C0C4CC;
}
.empty-day-line {
  width: 2px;
  height: 16px;
  background: #C0C4CC;
}

/* 文字 */
.empty-title {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}
.empty-desc {
  font-size: 13px;
  color: #909399;
  line-height: 1.7;
  margin: 0 0 24px;
}

/* 功能介绍 */
.empty-features {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  max-width: 240px;
}
.empty-feature {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #F5F7FA;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
}
.empty-feature-icon {
  font-size: 18px;
  flex-shrink: 0;
}

/* ===== 行程标题 ===== */
.canvas-title-text {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
  line-height: 1.4;
  word-break: break-word;
}

/* ===== 概览信息行 ===== */
.canvas-summary {
  display: flex;
  gap: 24px;
  padding: 4px 0;
  font-size: 13px;
  color: #606266;
}
.summary-item {
  display: inline-flex;
  align-items: center;
}

/* ===== 时间线 ===== */
.canvas-timeline {
  position: relative;
}

/* ===== 天数区块 ===== */
.day-section {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 20px;
}

/* 时间线节点 */
.day-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  width: 26px;
  padding-top: 4px;
  align-self: stretch;
}
.day-num {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #3385FF;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.day-line {
  width: 2px;
  flex: 1;
  background: #E5E9F0;
  margin-top: 8px;
  min-height: 20px;
}

/* 天数内容区 */
.day-body {
  flex: 1;
  min-width: 0;
  padding-bottom: 4px;
}
.day-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}
.day-label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.day-count {
  font-size: 12px;
  color: #909399;
}

/* ===== 行程项列表 ===== */
.day-items-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ===== 行程项卡片 ===== */
.travel-item {
  border-radius: 6px;
  background: #fff;
  border: 1px solid #E4E7ED;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s;
}
.travel-item:hover { border-color: #C0C4CC; }
.travel-item--expanded { border-color: #3385FF; }

/* 行程项头部 */
.travel-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
}

/* 类型徽章 */
.travel-item-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}
.badge-icon {
  font-size: 13px;
  line-height: 1;
}
.badge--spot     { background: #ECF5FF; color: #409EFF; }
.badge--food     { background: #FDF6EC; color: #E6A23C; }
.badge--traffic  { background: #E8F8F2; color: #2EBA8E; }
.badge--hotel    { background: #F0EBF8; color: #8E6ED5; }
.badge--relax    { background: #FDE8F0; color: #E57199; }
.badge--shopping { background: #FEF0E8; color: #F78935; }
.badge--default  { background: #F2F3F5; color: #909399; }

/* 名称 */
.travel-item-name {
  font-size: 14px;
  color: #303133;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 时间段 */
.travel-item-time {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  color: #8E6ED5;
  background: #F0EBF8;
  flex-shrink: 0;
}

/* 展开提示 */
.travel-item-expand-hint {
  font-size: 11px;
  color: #C0C4CC;
  flex-shrink: 0;
  user-select: none;
  white-space: nowrap;
}
.travel-item:hover .travel-item-expand-hint,
.travel-item--expanded .travel-item-expand-hint {
  color: #909399;
}

/* 删除按钮 */
.item-delete-btn {
  padding: 3px 10px;
  border: 1px solid #E4E7ED;
  border-radius: 4px;
  background: #fff;
  color: #909399;
  font-size: 12px;
  cursor: pointer;
  flex-shrink: 0;
  font-family: inherit;
  transition: all 0.15s;
  outline: none;
}
.item-delete-btn:hover {
  border-color: #F56C6C;
  color: #F56C6C;
  background: #FEF0F0;
}

/* ===== 展开详情 ===== */
.travel-item-detail {
  padding: 12px 14px 14px;
  border-top: 1px solid #EBEEF5;
  background: #F5F7FA;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.6;
}
.detail-label {
  color: #909399;
  flex-shrink: 0;
}
.detail-value {
  color: #303133;
  word-break: break-word;
}
code {
  font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #606266;
  background: #ECECEC;
  padding: 2px 6px;
  border-radius: 3px;
}

.detail-map-link {
  color: #409EFF;
  font-size: 12px;
  text-decoration: none;
}
.detail-map-link:hover { text-decoration: underline; }

/* ===== 空天占位 ===== */
.day-empty {
  padding: 14px;
  text-align: center;
  color: #C0C4CC;
  font-size: 12px;
  border: 1px dashed #E4E7ED;
  border-radius: 4px;
}

/* ===== 动画 ===== */
.item-detail-enter-active,
.item-detail-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}
.item-detail-enter-from,
.item-detail-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.item-list-enter-active { transition: all 0.2s ease; }
.item-list-leave-active { transition: all 0.15s ease; position: absolute; }
.item-list-enter-from { opacity: 0; transform: translateX(-8px); }
.item-list-leave-to { opacity: 0; transform: translateX(8px); }
.item-list-move { transition: transform 0.2s ease; }
</style>
