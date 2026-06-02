﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="customize-shell">
    <div class="customize-main">
      <header class="top-bar">
        <button type="button" class="back-btn" @click="goBack">← 返回</button>
        <h1>{{ isEditMode ? '编辑行程计划' : '创建行程计划' }}</h1>
      </header>

      <div class="content-scroll" ref="contentScrollRef">
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
        </section>

        <section class="card" v-if="step === 2">
          <div v-if="!editablePlan" class="empty">尚未生成或加载方案。</div>
          <template v-else>

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
                 <button class="secondary eph-btn" @click="handleGoBackToStep1">
                   <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
                   返回需求
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
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a10 10 0 0110 10c0 5.52-4.48 10-10 10S2 17.52 2 12"/><path d="m12 12-.5-8"/></svg>
                  AI 智能优化
                </span>
                <span class="ai-refine-hint">描述你的修改意图，AI 将自动调整行程</span>
              </div>
              <textarea v-model="refinePrompt" rows="2"
                placeholder="例如：把第一天的景点换成室内的、晚餐都安排在酒店附近、增加一个博物馆..." />
              <div class="ai-refine-actions">
                <button class="primary mini ai-send-btn" @click="handleAiRefine" :disabled="isRefining || !refinePrompt.trim()">
                  {{ isRefining ? 'AI 思考中...' : '发送给 AI' }}
                </button>
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
          </template>
        </section>

        <section class="card" v-if="step === 3">
          <h2>行程详情预览</h2>
          <p class="hint">方案已保存，你可以继续编辑或返回列表查看。</p>
          <div class="saved-preview">
            <div class="saved-preview-header"><h3>行程详情预览</h3><span class="saved-tag">已保存</span></div>
            <div class="saved-overview">
              <p><strong>标题：</strong>{{ editablePlan?.title || '(未命名)' }}</p>
              <p><strong>总天数：</strong>{{ editablePlan?.totalDays || 0 }} 天</p>
              <p><strong>预算：</strong>{{ formatCurrency(editablePlan?.estimatedCost || 0) }}</p>
              <p><strong>路线项：</strong>{{ routeItemCount }} 个</p>
            </div>
            <div class="preview-days">
              <div class="preview-day" v-for="day in editablePlan?.dailyPlans" :key="`pv-${day.dayNumber}`">
                <p class="preview-day-title">第 {{ day.dayNumber }} 天</p>
                <ul>
                  <li v-for="(item, i) in day.items" :key="i">
                    <span>{{ getTypeIcon(item.type) }}</span><span>{{ item.name }}</span>
                  </li>
                </ul>
              </div>
            </div>
            <div class="form-actions">
              <button class="secondary" @click="continueEditing">继续编辑</button>
              <button class="primary" @click="goBackToList">返回列表</button>
            </div>
          </div>
        </section>
      </div>
    </div>

    <aside class="agent-panel" v-if="(step === 1 && isLoadingGenerate) || (step === 2 && isRefining)">
      <div class="ap-head">
        <span class="ap-head-dot pulse"></span>
        <span class="ap-head-title">{{ isRefining ? 'Agent 正在优化方案' : 'Agent 正在生成方案' }}</span>
      </div>

      <div class="ap-progress-bar">
        <div class="ap-progress-segment" v-for="(p, i) in ['规划','执行','验证']" :key="i"
          :class="{ active: getPhaseProgress(i) === 'active', done: getPhaseProgress(i) === 'done' }">
          <span class="ap-progress-dot">{{ getPhaseProgress(i) === 'done' ? '&#10003;' : i + 1 }}</span>
          <span class="ap-progress-label">{{ p }}</span>
        </div>
        <div class="ap-progress-line" :style="{ width: progressLineWidth }"></div>
      </div>

      <div class="ap-action-bar" v-if="currentFocusAction">
        <span class="ap-action-icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
        </span>
        <span class="ap-action-text">{{ currentFocusAction }}</span>
        <span class="ap-action-bar-shimmer"></span>
      </div>

      <div class="ap-body" ref="agentLogRef">
        <template v-if="groupedLogs.length > 0">
          <div v-for="(group, gi) in groupedLogs" :key="'g'+gi" class="ap-group">
            <div class="ap-phase-row" :class="{ done: group.entries.some(e => e.phaseType?.endsWith('_END')) }" @click="toggleGroupExpand(group.phase)">
              <div class="ap-phase-node" :class="{ active: expandedGroups.has(group.phase) }">
                <span v-if="group.entries.some(e => e.phaseType?.endsWith('_END'))" class="ap-node-check">&#10003;</span>
                <span v-else class="ap-node-num">{{ gi + 1 }}</span>
              </div>
              <span class="ap-phase-name">{{ getPhaseGroupName(group.phase) }}</span>
              <span class="ap-phase-count">{{ group.entries.filter(e => e.type === 'tool').length }} 工具</span>
              <span class="ap-phase-arrow" :class="{ open: expandedGroups.has(group.phase) }">&#8250;</span>
            </div>

            <transition name="ap-slide">
              <div v-if="expandedGroups.has(group.phase)" class="ap-phase-body">
                <div v-for="(entry, ei) in group.entries" :key="ei" class="ap-entry">
                  <template v-if="entry.type === 'thought' && !isEmptyEntry(entry)">
                    <div class="ap-thought" :class="{ collapsed: !isThoughtExpanded(getGlobalIndex(gi, ei)) }" @click="toggleActiveThought(getGlobalIndex(gi, ei))">
                      <div class="ap-thought-bar"></div>
                      <div class="ap-thought-inner">
                        <div class="ap-thought-header">
                          <span class="ap-thought-icon">
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"/></svg>
                          </span>
                          <span class="ap-thought-label">推理</span>
                          <span class="ap-thought-toggle">{{ isThoughtExpanded(getGlobalIndex(gi, ei)) ? '收起' : '展开' }}</span>
                        </div>
                        <div v-if="isThoughtExpanded(getGlobalIndex(gi, ei))" class="ap-thought-full" v-html="formatThoughtMessage(entry.message)"></div>
                        <div v-else class="ap-thought-brief">{{ getThoughtPreview(entry.message) }}</div>
                      </div>
                    </div>
                  </template>
                  <template v-else-if="entry.type === 'tool' && !isEmptyEntry(entry)">
                    <div class="ap-tool">
                      <span class="ap-tool-dot" :class="getToolStatusClass(entry.message)"></span>
                      <span class="ap-tool-icon">{{ getToolIcon(entry.message) }}</span>
                      <span class="ap-tool-name">{{ extractToolName(entry.message) }}</span>
                      <span class="ap-tool-status" :class="getToolStatusClass(entry.message)">{{ getToolStatusText(entry.message) }}</span>
                    </div>
                  </template>
                </div>
              </div>
            </transition>
          </div>
        </template>

        <div class="ap-cursor" v-if="activeAgentLogs.length > 0">
          <span class="ap-cursor-dot"></span>
          <span class="ap-cursor-text">{{ currentFocusAction || '处理中...' }}</span>
        </div>
      </div>

      <div class="ap-stats-bar" v-if="activeAgentLogs.length > 0">
        <span class="ap-stats-item">
          <span class="ap-stats-num">{{ thoughtLogCount }}</span> 思考
        </span>
        <span class="ap-stats-divider"></span>
        <span class="ap-stats-item">
          <span class="ap-stats-num">{{ toolLogCount }}</span> 工具
        </span>
        <span class="ap-stats-divider"></span>
        <span class="ap-stats-item">
          <span class="ap-stats-num">{{ phaseLogCount }}</span> 阶段
        </span>
      </div>

      <div class="ap-empty" v-if="activeAgentLogs.length === 0">
        <div class="ap-empty-ring">
          <span class="ap-empty-spinner"></span>
        </div>
        <span class="ap-empty-text">等待 Agent 响应...</span>
      </div>
    </aside>

    <aside class="agent-panel" v-else-if="step === 1">
      <div class="ap-head">
        <span class="ap-head-dot idle"></span>
        <span class="ap-head-title">Agent</span>
      </div>
      <div class="ap-idle">
        <div class="ap-idle-robot">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="10" rx="2"/><circle cx="12" cy="5" r="2"/><path d="M12 7v4"/><line x1="8" y1="16" x2="8" y2="16"/><line x1="16" y1="16" x2="16" y2="16"/></svg>
        </div>
        <div class="ap-idle-title">AI 旅行规划助手</div>
        <p class="ap-idle-hint">填写左侧需求表单，点击<br/><strong>"生成 AI 方案"</strong></p>
        <div class="ap-idle-features">
          <div class="ap-idle-feat">
            <span class="ap-idle-feat-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            </span>
            <span>实时展示 Agent 思考过程</span>
          </div>
          <div class="ap-idle-feat">
            <span class="ap-idle-feat-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
            </span>
            <span>自动调用地图、天气等工具</span>
          </div>
          <div class="ap-idle-feat">
            <span class="ap-idle-feat-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            </span>
            <span>智能验证行程合理性</span>
          </div>
        </div>
      </div>
    </aside>

    <aside class="agent-panel" v-else-if="step >= 2 && !isRefining">
      <div class="ap-head">
        <span class="ap-head-dot done"></span>
        <span class="ap-head-title">生成完成</span>
      </div>

      <div class="ap-body">
        <div class="ap-done-metrics">
          <div class="ap-metric">
            <span class="ap-metric-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            </span>
            <span class="ap-metric-val">{{ editablePlan?.totalDays || '-' }}</span>
            <span class="ap-metric-label">天</span>
          </div>
          <div class="ap-metric">
            <span class="ap-metric-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="3 6 9 3 15 6 21 3 21 18 15 21 9 18 3 21"/><line x1="9" y1="3" x2="9" y2="18"/><line x1="15" y1="6" x2="15" y2="21"/></svg>
            </span>
            <span class="ap-metric-val">{{ routeItemCount }}</span>
            <span class="ap-metric-label">路线项</span>
          </div>
          <div class="ap-metric">
            <span class="ap-metric-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>
            </span>
            <span class="ap-metric-val">{{ formatCurrency(editablePlan?.estimatedCost || 0) }}</span>
            <span class="ap-metric-label">预算</span>
          </div>
          <div class="ap-metric" v-if="activeStats">
            <span class="ap-metric-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
            </span>
            <span class="ap-metric-val">{{ activeStats.totalTokens.toLocaleString() }}</span>
            <span class="ap-metric-label">Tokens</span>
          </div>
        </div>

        <template v-if="activeStats">
          <div class="ap-done-section">
            <div class="ap-done-section-head">
              <span class="ap-done-section-title">Token 消耗</span>
            </div>
            <div class="ap-token-bar">
              <div class="ap-token-segment prompt" :style="{ width: tokenPromptPercent + '%' }"></div>
              <div class="ap-token-segment completion" :style="{ width: tokenCompletionPercent + '%' }"></div>
            </div>
            <div class="ap-token-legend">
              <span class="ap-token-item"><span class="ap-token-dot prompt"></span>提示 {{ activeStats.totalPromptTokens.toLocaleString() }}</span>
              <span class="ap-token-item"><span class="ap-token-dot completion"></span>生成 {{ activeStats.totalCompletionTokens.toLocaleString() }}</span>
              <span class="ap-token-item"><span class="ap-token-dot total"></span>API {{ activeStats.planCalls + activeStats.executeCalls + activeStats.verifyCalls }} 次</span>
            </div>
          </div>
        </template>

        <template v-if="activeStats && activeStats.toolCalls && activeStats.toolCalls.length > 0">
          <div class="ap-done-section">
            <div class="ap-done-section-head">
              <span class="ap-done-section-title">工具调用</span>
              <span class="ap-done-section-badge">{{ activeStats.toolCalls.length }}</span>
            </div>
            <div class="ap-done-tools">
              <div v-for="(tc, idx) in activeStats.toolCalls" :key="idx" class="ap-done-tool">
                <span class="ap-tool-dot" :class="tc.status === 'success' ? 'status-success' : 'status-skipped'"></span>
                <span class="ap-done-tool-name">{{ tc.displayName }}</span>
              </div>
            </div>
            <div class="ap-tool-stats-row">
              <span class="ap-tool-stat success">{{ toolSuccessCount }} 成功</span>
              <span class="ap-tool-stat skipped" v-if="toolSkippedCount > 0">{{ toolSkippedCount }} 跳过</span>
            </div>
          </div>
        </template>

        <template v-if="generatingLogs.length > 0">
          <div class="ap-done-section">
            <div class="ap-done-section-head">
              <span class="ap-done-section-title">执行记录</span>
              <button class="ap-done-toggle" @click="toggleSummaryDetail">{{ showFullSummary ? '收起' : '展开全部' }}</button>
            </div>

            <div v-if="!showFullSummary" class="ap-done-timeline">
              <div v-for="(phase, idx) in summaryPhases" :key="'sp'+idx"
                class="ap-done-step" :class="{ done: phase.phaseType?.endsWith('_END') }">
                <span class="ap-done-step-dot" :class="{ done: phase.phaseType?.endsWith('_END') }"></span>
                <span class="ap-done-step-name">{{ getPhaseShortName(phase.phaseType) }}</span>
                <span class="ap-done-step-msg" v-if="isPhaseSummary(phase.message)">{{ phase.message }}</span>
              </div>
            </div>

            <div v-else class="ap-done-fulllog">
              <template v-for="(entry, idx) in filteredGeneratingLogs" :key="'lg'+idx">
                <div v-if="entry.type === 'phase'" class="ap-done-log-phase">
                  <span class="ap-done-log-icon">{{ entry.phaseType?.endsWith('_END') ? '&#10003;' : '&#8250;' }}</span>
                  <span>{{ entry.message }}</span>
                </div>
                <div v-else-if="entry.type === 'thought' && !isEmptyEntry(entry)" class="ap-done-log-thought">
                  <span class="ap-done-log-prefix">推理</span>
                  <span>{{ isJsonMessage(entry.message) ? cleanJsonPreview(entry.message) : truncateText(entry.message, 120) }}</span>
                </div>
                <div v-else-if="entry.type === 'tool' && !isEmptyEntry(entry)" class="ap-done-log-tool">
                  <span class="ap-tool-dot" :class="getToolStatusClass(entry.message)"></span>
                  <span>{{ getToolIcon(entry.message) }}</span>
                  <span>{{ extractToolName(entry.message) }}</span>
                </div>
              </template>
            </div>
          </div>
        </template>

        <div class="ap-done-section" v-else>
          <p class="ap-done-empty">暂无执行记录</p>
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  generateItineraryStream,
  modifyItineraryStream,
  getItineraryDetail,
  saveItinerary,
  updateItinerary
} from '../../api/itinerary'
import type { ItineraryPlan, ItineraryRequest, PhaseData, GenerationStats, ToolCallRecord } from '../../api/itinerary'
import { linkItinerary, getConversationByItineraryId } from '../../api/conversation'
import { goBackByAppRule } from '../../composables/useBackHandler'

const router = useRouter()
const route = useRoute()
const goBack = () => goBackByAppRule(router)

interface LogEntry {
  type: 'phase' | 'thought' | 'tool'
  message: string
  phaseType?: string
}

const step = ref(1)
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

const tokenPromptPercent = computed(() => {
  if (!activeStats.value) return 50
  const total = activeStats.value.totalTokens || 1
  return Math.round((activeStats.value.totalPromptTokens / total) * 100)
})

const tokenCompletionPercent = computed(() => {
  if (!activeStats.value) return 50
  return 100 - tokenPromptPercent.value
})

const toolSuccessCount = computed(() => {
  if (!activeStats.value?.toolCalls) return 0
  return activeStats.value.toolCalls.filter(t => t.status === 'success').length
})

const toolSkippedCount = computed(() => {
  if (!activeStats.value?.toolCalls) return 0
  return activeStats.value.toolCalls.filter(t => t.status !== 'success').length
})

const phaseLogCount = computed(() => activeAgentLogs.value.filter(e => e.type === 'phase').length)
const thoughtLogCount = computed(() => activeAgentLogs.value.filter(e => e.type === 'thought').length)
const toolLogCount = computed(() => activeAgentLogs.value.filter(e => e.type === 'tool').length)

const phaseProgressMap: Record<string, number> = { PLAN_START: 0, PLAN_END: 1, EXECUTE_START: 1, EXECUTE_END: 2, VERIFY_START: 2, VERIFY_END: 3 }
const getPhaseProgress = (stepIndex: number): 'pending' | 'active' | 'done' => {
  const current = currentAgentPhase.value
  const progress = phaseProgressMap[current] ?? -1
  if (stepIndex < progress) return 'done'
  if (stepIndex === progress) return 'active'
  return 'pending'
}

const progressLineWidth = computed(() => {
  const current = currentAgentPhase.value
  const progress = phaseProgressMap[current] ?? 0
  return Math.min(progress / 3 * 100, 100) + '%'
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
    const recentThree = thoughtIndices.slice(-3)
    const newExpanded = new Set(expandedActiveThoughts.value)
    for (const idx of recentThree) {
      if (!manuallyToggledThoughts.value.has(idx)) {
        newExpanded.add(idx)
      }
    }
    for (const idx of thoughtIndices.slice(0, -3)) {
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

const isThoughtExpanded = (idx: number): boolean => {
  return expandedActiveThoughts.value.has(idx)
}

const toggleActiveThought = (idx: number) => {
  manuallyToggledThoughts.value.add(idx)
  const newSet = new Set(expandedActiveThoughts.value)
  if (newSet.has(idx)) {
    newSet.delete(idx)
  } else {
    newSet.add(idx)
  }
  expandedActiveThoughts.value = newSet
}

const getThoughtPreview = (msg: string): string => {
  let text = msg
  if (text.startsWith('THOUGHT:')) text = text.replace('THOUGHT:', '').trim()
  if (text.length <= 80) return text
  return text.substring(0, 80) + '...'
}

interface PhaseGroup {
  phase: string
  entries: LogEntry[]
}

const groupedLogs = computed((): PhaseGroup[] => {
  const logs = activeAgentLogs.value
  const groupMap = new Map<string, LogEntry[]>()
  const phaseOrder: string[] = []
  let currentPhase = 'INIT'

  for (const entry of logs) {
    if (entry.type === 'phase') {
      currentPhase = entry.phaseType?.replace(/_START$|_END$/, '') || 'UNKNOWN'
    }
    if (!groupMap.has(currentPhase)) {
      groupMap.set(currentPhase, [])
      phaseOrder.push(currentPhase)
    }
    groupMap.get(currentPhase)!.push(entry)
  }

  return phaseOrder
    .map(phase => ({ phase, entries: groupMap.get(phase)! }))
    .filter(group => group.entries.some(e => e.type === 'thought' || e.type === 'tool'))
})

const toggleGroupExpand = (phase: string) => {
  const newSet = new Set(expandedGroups.value)
  if (newSet.has(phase)) {
    newSet.delete(phase)
  } else {
    newSet.add(phase)
  }
  expandedGroups.value = newSet
}

const getGlobalIndex = (groupIdx: number, entryIdx: number): number => {
  let count = 0
  for (let i = 0; i < groupIdx; i++) {
    count += groupedLogs.value[i].entries.length
  }
  return count + entryIdx
}

const getPhaseGroupName = (phase: string): string => {
  const names: Record<string, string> = {
    PLAN: '规划阶段',
    EXECUTE: '执行阶段',
    VERIFY: '验证阶段',
    INIT: '初始化',
    UNKNOWN: '其他'
  }
  return names[phase] || '其他'
}

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

const getToolStatusClass = (message: string): string => {
  if (message.includes('completed') || message.includes('完成')) return 'status-success'
  if (message.includes('failed') || message.includes('失败') || message.includes('跳过')) return 'status-skipped'
  return 'status-running'
}

const getToolStatusText = (message: string): string => {
  if (message.includes('completed') || message.includes('完成')) return '已完成'
  if (message.includes('failed') || message.includes('失败')) return '失败'
  if (message.includes('跳过')) return '已跳过'
  return '执行中'
}

const formatThoughtMessage = (msg: string): string => {
  let formatted = msg
  if (formatted.startsWith('THOUGHT:')) {
    formatted = formatted.replace('THOUGHT:', '').trim()
  }
  formatted = formatted.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  formatted = formatted.replace(/`(.*?)`/g, '<code>$1</code>')
  return formatted
}

// ===== 新增：位置信息和地图功能 =====
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

// 过滤空白和无效的日志条目
const filteredGeneratingLogs = computed(() => {
  return generatingLogs.value.filter(entry => !isEmptyEntry(entry))
})

// 检查是否为空白的entry
const isEmptyEntry = (entry: LogEntry): boolean => {
  if (!entry.message) return true
  const trimmed = entry.message.trim()
  if (trimmed.length === 0) return true
  if (trimmed === 'THOUGHT:') return true
  if (trimmed.startsWith('THOUGHT:') && trimmed.replace('THOUGHT:', '').trim().length === 0) return true
  return false
}

// 提取阶段节点（用于时间线显示）
const summaryPhases = computed(() => {
  return generatingLogs.value.filter(entry => entry.type === 'phase')
})

// 判断是否为阶段总结消息（非开始/结束标记）
const isPhaseSummary = (message: string): boolean => {
  return !!message && !message.includes('开始') && !message.includes('完成') && message.length > 10
}

// 获取阶段的简短名称
const getPhaseShortName = (phaseType?: string): string => {
  const names: Record<string, string> = {
    PLAN_START: '规划',
    PLAN_END: '✅ 规划完成',
    EXECUTE_START: '执行',
    EXECUTE_END: '✅ 执行完成',
    VERIFY_START: '验证',
    VERIFY_END: '✅ 验证完成'
  }
  return names[phaseType || ''] || phaseType || '未知'
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
  days: 3, interests: [], budgetLevel: 'medium', additionalNotes: ''
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
      (plan) => { editablePlan.value = normalizePlan(plan); step.value = 2; isLoadingGenerate.value = false },
      (error) => { errorMessage.value = error.message || '生成报错，请稍后重试。'; isLoadingGenerate.value = false },
      (stats) => { generationStats.value = stats },
      (convId) => { activeConversationId.value = convId }
    )
  } catch (error: any) { errorMessage.value = '生成失败，请稍后重试。'; isLoadingGenerate.value = false }
}

const handleGoBackToStep1 = async () => {
  if (!editablePlan.value) { step.value = 1; return }
  try {
    await ElMessageBox.confirm(
      '返回后将离开当前编辑页面，未保存的修改可能丢失。建议先保存方案。',
      '确认返回',
      { confirmButtonText: '继续返回', cancelButtonText: '留下编辑', type: 'info' }
    )
  } catch {
    return
  }
  step.value = 1
}

const handleAiRefine = async () => {
  if (!refinePrompt.value.trim() || !editablePlan.value) return
  isRefining.value = true; refineLogs.value = []; refineCurrentPhase.value = ''; refineStats.value = null
  const originalPlan = { ...editablePlan.value }

  try {
    await modifyItineraryStream(
      { ...request },
      { ...editablePlan.value },
      refinePrompt.value,
      activeConversationId.value,
      (phase: PhaseData) => { refineLogs.value.push({ type: 'phase', message: phase.message, phaseType: phase.type }); refineCurrentPhase.value = phase.type },
      (msg: string) => { refineLogs.value.push({ type: 'thought', message: msg }) },
      (msg: string) => { refineLogs.value.push({ type: 'tool', message: getLogMessage(msg) }) },
      (plan: ItineraryPlan) => {
        editablePlan.value = mergeRefinedPlan(originalPlan, normalizePlan(plan))
        isRefining.value = false
        refinePrompt.value = ''
      },
      (error: any) => { ElMessageBox.alert(error.message || '修改报错', 'AI修改失败', { type: 'error' }); isRefining.value = false },
      (stats: GenerationStats) => { refineStats.value = stats }
    )
  } catch (error: any) { ElMessageBox.alert(error.message || '修改失败', 'AI修改失败', { type: 'error' }); isRefining.value = false }
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
    savedItineraryId.value = r.data.data.id; step.value = 2
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
      savedItineraryId.value = itineraryId.value; step.value = 3
    } else {
      const r = await saveItinerary({ ...editablePlan.value, destination: request.destination.trim(), dailyPlans: editablePlan.value.dailyPlans, stats })
      savedItineraryId.value = r.data.data; step.value = 3
      if (activeConversationId.value && savedItineraryId.value) {
        linkItinerary(activeConversationId.value, savedItineraryId.value).catch(() => {})
      }
    }
  } catch { errorMessage.value = '保存失败，请稍后重试。' } finally { isSaving.value = false }
}

const continueEditing = () => { step.value = 2 }
const goBackToList = () => { router.push('/battle') }

onMounted(async () => {
  if (route.query.destination) request.destination = String(route.query.destination)
  if (itineraryId.value) await loadDetailForEdit()
})
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

.ai-refine-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.ai-send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

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

.saved-preview {
  border: 1px solid rgba(51, 133, 255, 0.1);
  border-radius: 16px;
  padding: 24px;
}

.saved-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(51, 133, 255, 0.08);
}

.saved-preview-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.saved-tag {
  border-radius: 20px;
  background: #dcfce7;
  color: #166534;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 700;
}

.saved-overview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 20px;
  background: #FAFCFF;
  padding: 16px;
  border-radius: 12px;
}

.saved-overview p {
  margin: 0;
  font-size: 14px;
  color: #6B8CBE;
}

.saved-overview strong {
  color: #1E3A5F;
  font-weight: 600;
}

.preview-days {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-day {
  border: 1px solid rgba(51, 133, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
}

.preview-day-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
  color: #3385FF;
}

.preview-day ul {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-day li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  padding: 8px 12px;
  background: #FAFCFF;
  border-radius: 8px;
}

.bg-spot { background: #e0f2fe; color: #0284c7; }
.bg-food { background: #ffedd5; color: #ea580c; }
.bg-traffic { background: #f3e8ff; color: #7c3aed; }
.bg-hotel { background: #dbeafe; color: #2563eb; }
.bg-relax { background: #dcfce7; color: #16a34a; }
.bg-shopping { background: #fce7f3; color: #db2777; }
.bg-default { background: #f1f5f9; color: #475569; }

/* ============ Agent Panel ============ */

.agent-panel {
  width: 420px;
  min-width: 420px;
  height: 100dvh;
  border-left: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: -4px 0 24px rgba(0,0,0,0.03);
}

/* --- Head --- */

.ap-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 22px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
  background: #fff;
}

.ap-head-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ap-head-dot.pulse {
  background: #3b82f6;
  animation: ap-pulse 1.5s ease-in-out infinite;
}

.ap-head-dot.idle { background: #cbd5e1; }
.ap-head-dot.done { background: #22c55e; }

@keyframes ap-pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgba(59,130,246,0.4); }
  50% { opacity: 0.7; box-shadow: 0 0 0 8px rgba(59,130,246,0); }
}

.ap-head-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  letter-spacing: -0.01em;
}

/* --- Progress Bar --- */

.ap-progress-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 28px;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
  position: relative;
  flex-shrink: 0;
}

.ap-progress-segment {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  z-index: 1;
}

.ap-progress-dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  transition: all 0.3s ease;
}

.ap-progress-segment.active .ap-progress-dot {
  background: #3b82f6;
  color: #fff;
  box-shadow: 0 0 0 4px rgba(59,130,246,0.15), 0 2px 8px rgba(59,130,246,0.3);
  animation: ap-dot-glow 2s ease-in-out infinite;
}

.ap-progress-segment.done .ap-progress-dot {
  background: #22c55e;
  color: #fff;
  box-shadow: 0 2px 6px rgba(34,197,94,0.25);
}

@keyframes ap-dot-glow {
  0%, 100% { box-shadow: 0 0 0 4px rgba(59,130,246,0.15), 0 2px 8px rgba(59,130,246,0.3); }
  50% { box-shadow: 0 0 0 6px rgba(59,130,246,0.1), 0 2px 12px rgba(59,130,246,0.4); }
}

.ap-progress-label {
  font-size: 11px;
  font-weight: 500;
  color: #94a3b8;
  transition: color 0.3s;
}

.ap-progress-segment.active .ap-progress-label { color: #3b82f6; font-weight: 600; }
.ap-progress-segment.done .ap-progress-label { color: #22c55e; }

.ap-progress-line {
  position: absolute;
  left: 28px;
  top: 50%;
  transform: translateY(-50%);
  height: 3px;
  background: linear-gradient(90deg, #22c55e, #3b82f6);
  border-radius: 2px;
  transition: width 0.6s ease;
  z-index: 0;
  margin-top: -12px;
}

/* --- Action Bar (current action spotlight) --- */

.ap-action-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 22px;
  background: linear-gradient(90deg, #eff6ff, #f0fdf4);
  border-bottom: 1px solid #dbeafe;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

.ap-action-icon {
  color: #3b82f6;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.ap-action-text {
  font-size: 12px;
  font-weight: 500;
  color: #1e40af;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
  flex: 1;
}

.ap-action-bar-shimmer {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.4), transparent);
  animation: ap-shimmer 2.5s ease-in-out infinite;
}

@keyframes ap-shimmer {
  0% { left: -100%; }
  100% { left: 100%; }
}

/* --- Body (scroll area) --- */

.ap-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 22px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ap-body::-webkit-scrollbar { width: 4px; }
.ap-body::-webkit-scrollbar-track { background: transparent; }
.ap-body::-webkit-scrollbar-thumb { background: #e2e8f0; border-radius: 2px; }

/* --- Group (phase section) --- */

.ap-group {
  margin-bottom: 6px;
}

.ap-phase-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.15s;
  border-radius: 6px;
}

.ap-phase-row:hover {
  background: #f8fafc;
}

.ap-phase-row.done .ap-phase-name { color: #16a34a; }

.ap-phase-node {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  transition: all 0.2s;
}

.ap-phase-node.active {
  background: #eff6ff;
  color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59,130,246,0.12);
}

.ap-node-check {
  font-size: 11px;
  color: #22c55e;
}

.ap-node-num {
  font-size: 11px;
  font-weight: 700;
}

.ap-phase-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.ap-phase-count {
  font-size: 10px;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.ap-phase-arrow {
  font-size: 16px;
  color: #94a3b8;
  transition: transform 0.2s;
  line-height: 1;
}

.ap-phase-arrow.open {
  transform: rotate(90deg);
}

/* --- Phase body (entries) --- */

.ap-phase-body {
  padding: 4px 0 8px 32px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-left: 2px solid #e2e8f0;
  margin-left: 11px;
}

.ap-entry { line-height: 1.5; }

/* --- Thought (rich card style) --- */

.ap-thought {
  display: flex;
  gap: 0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafbff;
  border: 1px solid #eef2ff;
  overflow: hidden;
}

.ap-thought:hover {
  border-color: #c7d2fe;
  box-shadow: 0 2px 8px rgba(99,102,241,0.08);
}

.ap-thought-bar {
  width: 3px;
  background: linear-gradient(180deg, #818cf8, #6366f1);
  flex-shrink: 0;
  border-radius: 3px 0 0 3px;
}

.ap-thought-inner {
  flex: 1;
  padding: 10px 12px;
  min-width: 0;
}

.ap-thought-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.ap-thought-icon {
  color: #818cf8;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.ap-thought-label {
  font-size: 10px;
  font-weight: 700;
  color: #6366f1;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  flex-shrink: 0;
}

.ap-thought-toggle {
  font-size: 10px;
  color: #818cf8;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(99,102,241,0.08);
  cursor: pointer;
  transition: all 0.15s;
  margin-left: auto;
  flex-shrink: 0;
}

.ap-thought-toggle:hover {
  background: rgba(99,102,241,0.15);
}

.ap-thought-brief {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.ap-thought-full {
  font-size: 12.5px;
  color: #475569;
  line-height: 1.7;
  min-width: 0;
  word-break: break-word;
}

.ap-thought-full strong { color: #334155; font-weight: 600; }
.ap-thought-full code {
  background: #eef2ff;
  color: #6366f1;
  padding: 1px 5px;
  border-radius: 4px;
  font-family: 'SF Mono', 'Monaco', 'Menlo', monospace;
  font-size: 11px;
}

.ap-thought.collapsed {
  background: #f8fafc;
  border-color: #f1f5f9;
}

.ap-thought.collapsed .ap-thought-bar {
  background: #cbd5e1;
}

.ap-thought.collapsed .ap-thought-brief {
  color: #94a3b8;
  font-style: italic;
}

/* --- Tool --- */

.ap-tool {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: #475569;
  background: #fff;
  border: 1px solid #f1f5f9;
  transition: background 0.15s;
}

.ap-tool:hover {
  background: #f8fafc;
}

.ap-tool-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ap-tool-dot.status-success { background: #22c55e; box-shadow: 0 0 4px rgba(34,197,94,0.3); }
.ap-tool-dot.status-running { background: #3b82f6; animation: ap-pulse 1.5s ease-in-out infinite; }
.ap-tool-dot.status-skipped { background: #f59e0b; }

.ap-tool-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.ap-tool-name {
  flex: 1;
  font-weight: 500;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ap-tool-status {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

.ap-tool-status.status-success { background: #f0fdf4; color: #16a34a; }
.ap-tool-status.status-running { background: #eff6ff; color: #2563eb; }
.ap-tool-status.status-skipped { background: #fffbeb; color: #d97706; }

/* --- Cursor --- */

.ap-cursor {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0 0;
  margin-top: 6px;
  border-top: 1px solid #f1f5f9;
}

.ap-cursor-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #3b82f6;
  animation: ap-blink 1s step-end infinite;
}

@keyframes ap-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.ap-cursor-text {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* --- Stats Bar --- */

.ap-stats-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 12px 22px;
  border-top: 1px solid #f1f5f9;
  background: #fff;
  flex-shrink: 0;
}

.ap-stats-item {
  font-size: 11px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 4px;
}

.ap-stats-num {
  font-weight: 700;
  color: #64748b;
  font-size: 13px;
}

.ap-stats-divider {
  width: 1px;
  height: 14px;
  background: #e2e8f0;
}

/* --- Empty state --- */

.ap-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.ap-empty-ring {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ap-empty-spinner {
  width: 22px;
  height: 22px;
  border: 2.5px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.ap-empty-text {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}

/* --- Idle state --- */

.ap-idle {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 28px;
  text-align: center;
}

.ap-idle-robot {
  opacity: 0.35;
  margin-bottom: 8px;
}

.ap-idle-title {
  font-size: 16px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 4px;
}

.ap-idle-hint {
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.7;
  margin-bottom: 20px;
}

.ap-idle-hint strong {
  color: #3b82f6;
  font-weight: 600;
}

.ap-idle-features {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  max-width: 260px;
}

.ap-idle-feat {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
  font-size: 12px;
  color: #64748b;
  text-align: left;
}

.ap-idle-feat-icon {
  color: #94a3b8;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

/* ============ Done State ============ */

.ap-done-metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  padding-bottom: 18px;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 18px;
}

.ap-metric {
  background: #f8fafc;
  border-radius: 10px;
  padding: 14px 12px;
  text-align: center;
  border: 1px solid #f1f5f9;
  position: relative;
}

.ap-metric-icon {
  position: absolute;
  top: 10px;
  right: 10px;
  color: #cbd5e1;
  display: flex;
}

.ap-metric-val {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
}

.ap-metric-label {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  margin-top: 3px;
  font-weight: 500;
}

.ap-done-section {
  margin-bottom: 18px;
}

.ap-done-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0 10px;
}

.ap-done-section-title {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.ap-done-section-badge {
  font-size: 10px;
  font-weight: 700;
  background: #f1f5f9;
  color: #64748b;
  padding: 2px 8px;
  border-radius: 10px;
}

.ap-done-toggle {
  font-size: 11px;
  color: #3b82f6;
  background: none;
  border: none;
  cursor: pointer;
  padding: 3px 10px;
  border-radius: 4px;
  font-weight: 600;
  transition: background 0.15s;
}

.ap-done-toggle:hover {
  background: #eff6ff;
}

/* --- Token Bar --- */

.ap-token-bar {
  height: 8px;
  border-radius: 4px;
  background: #f1f5f9;
  display: flex;
  overflow: hidden;
  margin-bottom: 10px;
}

.ap-token-segment {
  height: 100%;
  transition: width 0.5s ease;
}

.ap-token-segment.prompt {
  background: linear-gradient(90deg, #60a5fa, #3b82f6);
  border-radius: 4px 0 0 4px;
}

.ap-token-segment.completion {
  background: linear-gradient(90deg, #a78bfa, #7c3aed);
  border-radius: 0 4px 4px 0;
}

.ap-token-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.ap-token-item {
  font-size: 11px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 5px;
}

.ap-token-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.ap-token-dot.prompt { background: #3b82f6; }
.ap-token-dot.completion { background: #7c3aed; }
.ap-token-dot.total { background: #94a3b8; border-radius: 50%; }

/* --- Done tools list --- */

.ap-done-tools {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 200px;
  overflow-y: auto;
}

.ap-done-tool {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: #475569;
  border: 1px solid #f1f5f9;
  background: #fff;
}

.ap-done-tool-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ap-tool-stats-row {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
}

.ap-tool-stat {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 6px;
}

.ap-tool-stat.success { background: #f0fdf4; color: #16a34a; }
.ap-tool-stat.skipped { background: #fffbeb; color: #d97706; }

/* --- Done timeline --- */

.ap-done-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  position: relative;
  padding-left: 16px;
}

.ap-done-timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 10px;
  bottom: 10px;
  width: 2px;
  background: linear-gradient(180deg, #e2e8f0, #f1f5f9);
  border-radius: 1px;
}

.ap-done-step {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 8px 0;
  position: relative;
}

.ap-done-step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #e2e8f0;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
  margin-top: 2px;
}

.ap-done-step-dot.done {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34,197,94,0.12);
}

.ap-done-step-name {
  font-size: 12px;
  color: #94a3b8;
  flex-shrink: 0;
}

.ap-done-step.done .ap-done-step-name {
  color: #475569;
  font-weight: 600;
}

.ap-done-step-msg {
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
  min-width: 0;
}

/* --- Done full log --- */

.ap-done-fulllog {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 400px;
  overflow-y: auto;
  padding-right: 4px;
}

.ap-done-fulllog::-webkit-scrollbar { width: 3px; }
.ap-done-fulllog::-webkit-scrollbar-thumb { background: #e2e8f0; border-radius: 2px; }

.ap-done-log-phase {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  background: #f8fafc;
  border-radius: 6px;
  margin-top: 6px;
  border: 1px solid #f1f5f9;
}

.ap-done-log-icon {
  font-size: 11px;
  color: #22c55e;
  flex-shrink: 0;
  width: 14px;
  text-align: center;
}

.ap-done-log-thought {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 10px 4px 30px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.ap-done-log-prefix {
  font-size: 9px;
  font-weight: 700;
  color: #818cf8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  flex-shrink: 0;
  background: #eef2ff;
  padding: 1px 6px;
  border-radius: 3px;
}

.ap-done-log-tool {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 10px 3px 30px;
  font-size: 12px;
  color: #475569;
}

.ap-done-empty {
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  padding: 24px 0;
}

/* ============ Transitions ============ */

.ap-slide-enter-active,
.ap-slide-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}

.ap-slide-enter-from,
.ap-slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.ap-slide-enter-to,
.ap-slide-leave-from {
  opacity: 1;
  max-height: 2000px;
}

</style>
