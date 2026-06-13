<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
    getConversationDetail,
    updateConversation,
    type ConversationVO,
    type AgentMessageVO,
    type ConversationDetailVO
} from '../../api/conversation'
import { getToolDisplayName, getToolIcon } from '../../constants/tool'

const route = useRoute()
const router = useRouter()
const conversationId = route.params.id as string

const conversation = ref<ConversationVO | null>(null)
const messages = ref<AgentMessageVO[]>([])
const loading = ref(false)
const chatContainer = ref<HTMLElement | null>(null)
const showToolCalls = ref<Record<string, boolean>>({})
const showSystemMessages = ref(true)

const editDialogVisible = ref(false)
const editForm = ref({ title: '', tags: '', description: '' })



const typeIconMap: Record<string, string> = {
    '景点': '🏛',
    '餐饮': '🍜',
    '住宿': '🏨',
    '休闲': '🎭',
}

const chatMessages = computed(() =>
    messages.value.filter(m => m.role === 'user' || m.role === 'assistant')
)

const systemMessages = computed(() =>
    messages.value.filter(m => m.role === 'system')
)

async function fetchDetail() {
    loading.value = true
    try {
        const res = await getConversationDetail(conversationId)
        if (res.data && res.data.code === '200' && res.data.data) {
            const detail: ConversationDetailVO = res.data.data
            conversation.value = detail.conversation
            messages.value = detail.messages || []
            await scrollToBottom()
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '获取对话失败')
    } finally {
        loading.value = false
    }
}

async function scrollToBottom() {
    await nextTick()
    if (chatContainer.value) {
        chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
}

function formatTime(time: string) {
    if (!time) return ''
    const d = new Date(time)
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hours = String(d.getHours()).padStart(2, '0')
    const minutes = String(d.getMinutes()).padStart(2, '0')
    return `${month}-${day} ${hours}:${minutes}`
}

function toggleToolCalls(msgId: string) {
    showToolCalls.value[msgId] = !showToolCalls.value[msgId]
}

function parseToolCalls(toolCallsStr: string | null) {
    if (!toolCallsStr) return null
    try {
        const parsed = JSON.parse(toolCallsStr)
        if (parsed && typeof parsed === 'object' && parsed.dailyPlans) {
            return { type: 'itinerary', data: parsed }
        }
        return { type: 'tool', data: parsed }
    } catch {
        return null
    }
}

function renderToolCallPreview(toolCallsStr: string | null): string {
    const result = parseToolCalls(toolCallsStr)
    if (!result) return ''
    if (result.type === 'itinerary') {
        const plan = result.data
        const days = plan.dailyPlans?.length || 0
        const cost = plan.estimatedCost || 0
        const items = plan.dailyPlans?.reduce((sum: number, d: any) => sum + (d.items?.length || 0), 0) || 0
        return `${days}天行程 · ${items}个地点 · ¥${cost}`
    }
    // 工具调用：显示调用数量
    const data = result.data
    if (Array.isArray(data)) {
        return `🚀 ${data.length}次工具调用`
    }
    return '🔧 查看工具调用'
}

// 将工具调用数据解析为结构化列表
function parseStructuredToolCalls(toolCallsStr: string | null): Array<{name: string; displayName: string; icon: string; status: string; args?: string; result?: string}> | null {
    const result = parseToolCalls(toolCallsStr)
    if (!result || result.type !== 'tool') return null
    const data = result.data
    if (!Array.isArray(data)) return null
    return data.map((tc: any) => {
        const rawName = tc.name || tc.function?.name || ''
        const displayName = getToolDisplayName(rawName)
        const icon = getToolIcon(rawName)
        // 从参数中提取关键信息
        let argsSummary = ''
        let argsObj: any = tc.arguments || tc.function?.arguments || {}
        if (typeof argsObj === 'string') {
            try { argsObj = JSON.parse(argsObj) } catch {}
        }
        if (typeof argsObj === 'object' && argsObj !== null) {
            const keys = Object.keys(argsObj).filter(k => k !== '__arg_name')
            argsSummary = keys.slice(0, 2).map(k => `${k}: ${String(argsObj[k]).slice(0, 30)}`).join(' | ')
        }
        // 结果摘要
        let resultSummary = ''
        if (tc.output || tc.result) {
            const output = tc.output || tc.result
            if (typeof output === 'string') {
                resultSummary = output.length > 100 ? output.slice(0, 100) + '...' : output
            } else if (output?.content) {
                const content = Array.isArray(output.content) ? output.content[0]?.text : output.content
                resultSummary = String(content || '').slice(0, 100)
            }
        }
        return {
            name: rawName,
            displayName,
            icon,
            status: tc.status || (resultSummary?.includes('Error') ? 'failed' : 'success'),
            args: argsSummary,
            result: resultSummary,
        }
    })
}

// 解析行程数据用于结构化展示
function parseItineraryData(toolCallsStr: string | null): { title: string; days: number; cost: number; totalItems: number; dailyPlans: Array<{dayNumber: number; items: Array<{name: string; type: string}>}> } | null {
    const result = parseToolCalls(toolCallsStr)
    if (!result || result.type !== 'itinerary') return null
    const plan = result.data
    return {
        title: plan.title || '未命名行程',
        days: plan.dailyPlans?.length || 0,
        cost: plan.estimatedCost || 0,
        totalItems: plan.dailyPlans?.reduce((s: number, d: any) => s + (d.items?.length || 0), 0) || 0,
        dailyPlans: (plan.dailyPlans || []).map((d: any) => ({
            dayNumber: d.dayNumber,
            items: (d.items || []).map((i: any) => ({ name: i.name || '-', type: i.type || '-' })),
        })),
    }
}

function goToItinerary() {
    if (conversation.value?.itineraryId) {
        router.push(`/workspace?itineraryId=${conversation.value.itineraryId}&convId=${conversationId}`)
    } else {
        router.push('/workspace')
    }
}

function continueInWorkspace() {
    const params = new URLSearchParams()
    params.set('convId', conversationId)
    if (conversation.value?.itineraryId) {
        params.set('itineraryId', conversation.value.itineraryId)
    }
    router.push(`/workspace?${params.toString()}`)
}

function goNewPlan() {
    router.push('/workspace')
}

function openEditDialog() {
    if (!conversation.value) return
    editForm.value = {
        title: conversation.value.title || '',
        tags: conversation.value.tags || '',
        description: conversation.value.description || ''
    }
    editDialogVisible.value = true
}

async function handleEditSave() {
    if (!conversation.value) return
    try {
        const res = await updateConversation(conversationId, {
            title: editForm.value.title,
            tags: editForm.value.tags,
            description: editForm.value.description
        })
        if (res.data?.code === '200' && res.data?.data) {
            conversation.value = res.data.data
            ElMessage.success('对话信息已更新')
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '更新失败')
    } finally {
        editDialogVisible.value = false
    }
}

onMounted(() => {
    fetchDetail()
})
</script>

<template>
    <div class="chat-page">
        <div class="chat-header">
            <el-button text size="small" @click="router.push('/conversation')" class="back-btn">
                &larr; 返回
            </el-button>
            <div class="chat-header-title">
                <h2 class="chat-title">{{ conversation?.title || '对话详情' }}</h2>
                <span v-if="conversation?.description" class="chat-desc">{{ conversation.description }}</span>
            </div>
            <div class="chat-header-actions">
                <el-button
                    type="primary"
                    size="small"
                    @click="continueInWorkspace"
                    class="action-btn"
                    title="在工作台中继续此对话"
                >
                    🚀 继续规划
                </el-button>
                <el-button
                    v-if="conversation?.itineraryId"
                    size="small"
                    @click="goToItinerary"
                    class="action-btn-outline"
                >
                    📋 查看行程
                </el-button>
                <el-button text size="small" @click="openEditDialog" class="edit-btn" title="编辑对话信息">
                    ✏ 编辑
                </el-button>
                <el-button size="small" @click="goNewPlan" class="action-btn-outline">
                    &#65279; 新规划
                </el-button>
            </div>
            <div class="chat-header-tags" v-if="conversation?.tags">
                <el-tag
                    v-for="tag in conversation.tags.split(',').filter(Boolean)"
                    :key="tag"
                    size="small"
                >
                    {{ tag.trim() }}
                </el-tag>
            </div>
        </div>

        <div class="chat-body" ref="chatContainer" v-loading="loading">
            <div v-if="loading" class="loading-placeholder">
                <p>加载中...</p>
            </div>

            <template v-else>
                <div
                    v-for="msg in chatMessages"
                    :key="msg.id"
                    class="message-row"
                    :class="{ 'message-own': msg.role === 'user' }"
                >
                    <div class="message-avatar" :class="{
                        'avatar-user': msg.role === 'user',
                        'avatar-assistant': msg.role === 'assistant'
                    }">
                        {{ msg.role === 'user' ? '👤' : '🤖' }}
                    </div>
                    <div class="message-bubble-wrapper">
                        <div class="message-bubble" :class="{
                            'bubble-user': msg.role === 'user',
                            'bubble-assistant': msg.role === 'assistant'
                        }">
                            <div class="message-content">{{ msg.content }}</div>

                            <!-- 结构化工具调用/行程结果 -->
                            <div v-if="msg.toolCalls" class="message-tool-calls">
                                <!-- 行程结果卡片 -->
                                <template v-if="parseItineraryData(msg.toolCalls)">
                                    <div class="itinerary-result-card" @click="toggleToolCalls(msg.id)">
                                        <div class="ir-header">
                                            <span class="ir-icon">📍</span>
                                            <span class="ir-title">{{ parseItineraryData(msg.toolCalls)?.title }}</span>
                                            <span class="ir-badge">{{ parseItineraryData(msg.toolCalls)?.days }}天</span>
                                            <span class="ir-badge ir-budget">¥{{ parseItineraryData(msg.toolCalls)?.cost }}</span>
                                            <span class="ir-toggle">{{ showToolCalls[msg.id] ? '▲' : '▼' }}</span>
                                        </div>
                                        <transition name="ir-expand">
                                            <div v-if="showToolCalls[msg.id]" class="ir-body">
                                                <div v-for="day in parseItineraryData(msg.toolCalls)?.dailyPlans" :key="day.dayNumber" class="ir-day">
                                                    <div class="ir-day-header">
                                                        <span class="ir-day-num">{{ day.dayNumber }}</span>
                                                        <span class="ir-day-label">Day {{ day.dayNumber }}</span>
                                                        <span class="ir-day-count">{{ day.items.length }} 个地点</span>
                                                    </div>
                                                    <div class="ir-items">
                                                        <div v-for="(item, ii) in day.items" :key="ii" class="ir-item">
                                                            <span class="ir-item-icon">{{ typeIconMap[item.type] || '📍' }}</span>
                                                            <span class="ir-item-name">{{ item.name }}</span>
                                                            <span class="ir-item-type">{{ item.type }}</span>
                                                        </div>
                                                    </div>
                                                </div>
                                                <button class="ir-action-btn" @click.stop="goToItinerary">
                                                            📋 在行程编辑器中打开
                                                        </button>
                                            </div>
                                        </transition>
                                    </div>
                                </template>

                                <!-- 工具调用列表 -->
                                <template v-else-if="parseStructuredToolCalls(msg.toolCalls)">
                                    <div class="tools-result-card" @click="toggleToolCalls(msg.id)">
                                        <div class="tr-header">
                                            <span class="tr-icon">🚀</span>
                                            <span class="tr-title">{{ parseStructuredToolCalls(msg.toolCalls)?.length }} 次工具调用</span>
                                            <span class="tr-toggle">{{ showToolCalls[msg.id] ? '▲' : '▼' }}</span>
                                        </div>
                                        <transition name="tr-expand">
                                            <div v-if="showToolCalls[msg.id]" class="tr-body">
                                                <div
                                                    v-for="(tc, ti) in parseStructuredToolCalls(msg.toolCalls)"
                                                    :key="ti"
                                                    class="tr-tool-item"
                                                    :class="{ 'tr-failed': tc.status === 'failed' }"
                                                >
                                                    <span class="tr-tool-status-dot" :class="tc.status"></span>
                                                    <span class="tr-tool-icon">{{ tc.icon }}</span>
                                                    <span class="tr-tool-name">{{ tc.displayName }}</span>
                                                    <span v-if="tc.args" class="tr-tool-args">{{ tc.args }}</span>
                                                </div>
                                                <!-- 原始 JSON 折叠保留（给开发者用） -->
                                                <details class="tr-raw-json">
                                                    <summary class="tr-raw-summary">📝 原始数据</summary>
                                                    <pre class="tr-raw-pre">{{ JSON.stringify(parseToolCalls(msg.toolCalls)?.data, null, 2) }}</pre>
                                                </details>
                                            </div>
                                        </transition>
                                    </div>
                                </template>

                                <!-- 兜底：无法识别的格式 -->
                                <template v-else>
                                    <el-button
                                        text
                                        size="small"
                                        type="info"
                                        @click="toggleToolCalls(msg.id)"
                                        class="tool-toggle-btn"
                                    >
                                        {{ renderToolCallPreview(msg.toolCalls) || '🔧 查看详情' }}
                                        {{ showToolCalls[msg.id] ? ' ▲' : ' ▼' }}
                                    </el-button>
                                    <div v-if="showToolCalls[msg.id]" class="tool-calls-expanded">
                                        <pre class="tool-calls-json">{{
                                            JSON.stringify(parseToolCalls(msg.toolCalls)?.data, null, 2)
                                        }}</pre>
                                    </div>
                                </template>
                            </div>
                        </div>
                        <div class="message-time">{{ formatTime(msg.createTime) }}</div>
                    </div>
                </div>

                <div v-if="systemMessages.length > 0" class="system-section">
                    <div class="system-toggle" @click="showSystemMessages = !showSystemMessages">
                        <span class="system-toggle-icon">{{ showSystemMessages ? '▼' : '▶' }}</span>
                        <span class="system-toggle-text">系统消息（{{ systemMessages.length }}条）</span>
                    </div>
                    <div v-if="showSystemMessages" class="system-messages">
                        <div v-for="msg in systemMessages" :key="msg.id" class="system-msg">
                            <span class="system-msg-dot">&middot;</span>
                            <span class="system-msg-text">{{ msg.content }}</span>
                            <span class="system-msg-time">{{ formatTime(msg.createTime) }}</span>
                        </div>
                    </div>
                </div>
            </template>
        </div>
    </div>

    <el-dialog v-model="editDialogVisible" title="编辑对话信息" width="480px">
        <el-form :model="editForm" label-position="top">
            <el-form-item label="对话标题">
                <el-input v-model="editForm.title" placeholder="输入对话标题" />
            </el-form-item>
            <el-form-item label="标签">
                <el-input v-model="editForm.tags" placeholder="多个标签用逗号分隔，如：旅行规划,北京,3天" />
            </el-form-item>
            <el-form-item label="描述">
                <el-input
                    v-model="editForm.description"
                    type="textarea"
                    :rows="3"
                    placeholder="输入对话描述"
                />
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="editDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleEditSave">保存</el-button>
        </template>
    </el-dialog>
</template>

<style scoped>
.chat-page {
    display: flex;
    flex-direction: column;
    height: 100dvh;
    max-width: 860px;
    margin: 0 auto;
    background: #fff;
}

.chat-header {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 16px 24px;
    border-bottom: 1px solid rgba(51, 133, 255, 0.08);
    flex-shrink: 0;
    flex-wrap: wrap;
}

.back-btn {
    color: #5B7FA5;
    font-size: 14px;
    flex-shrink: 0;
}

.back-btn:hover {
    color: #3385FF;
}

.chat-header-title {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.chat-title {
    font-size: 18px;
    font-weight: 700;
    color: #1E3A5F;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.chat-desc {
    font-size: 12px;
    color: #8BA8C8;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.chat-header-actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
}

.action-btn {
    border-radius: 8px;
    font-weight: 600;
}

.action-btn-outline {
    border-radius: 8px;
    border-color: rgba(51, 133, 255, 0.2);
    color: #3385FF;
}

.edit-btn {
    color: #5B7FA5;
    font-size: 13px;
    padding: 4px 8px;
}

.edit-btn:hover {
    color: #3385FF;
}

.chat-header-tags {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    width: 100%;
}

.chat-body {
    flex: 1;
    overflow-y: auto;
    padding: 20px 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    background: #F8FBFF;
}

.loading-placeholder {
    display: flex;
    justify-content: center;
    padding: 40px;
    color: #8BA8C8;
}

.message-row {
    display: flex;
    gap: 10px;
    align-items: flex-start;
}

.message-row.message-own {
    flex-direction: row-reverse;
}

.message-avatar {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    flex-shrink: 0;
}

.avatar-user {
    background: linear-gradient(135deg, #3385FF, #1A6FFF);
}

.avatar-assistant {
    background: linear-gradient(135deg, #FF9500, #FF6B00);
}

.message-bubble-wrapper {
    max-width: 78%;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.message-row.message-own .message-bubble-wrapper {
    align-items: flex-end;
}

.message-row:not(.message-own) .message-bubble-wrapper {
    align-items: flex-start;
}

.message-bubble {
    padding: 12px 16px;
    border-radius: 14px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-word;
}

.bubble-user {
    background: linear-gradient(135deg, #3385FF, #1A6FFF);
    color: #fff;
    border-bottom-right-radius: 4px;
}

.bubble-assistant {
    background: #fff;
    color: #1E3A5F;
    border: 1px solid rgba(51, 133, 255, 0.08);
    border-bottom-left-radius: 4px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.message-content {
    white-space: pre-wrap;
}

.message-time {
    font-size: 11px;
    color: #B0C8DE;
    padding: 0 4px;
}

/* ====== 工具调用区域 ====== */
.message-tool-calls {
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid rgba(51, 133, 255, 0.08);
}

.bubble-user .message-tool-calls {
    border-top-color: rgba(255, 255, 255, 0.15);
}

.bubble-assistant .message-tool-calls {
    border-top-color: rgba(51, 133, 255, 0.1);
}

/* ---- 行程结果卡片 ---- */
.itinerary-result-card {
    cursor: pointer;
    background: linear-gradient(135deg, #EEF4FF 0%, #F5F9FF 100%);
    border: 1px solid rgba(51, 133, 255, 0.12);
    border-radius: 12px;
    overflow: hidden;
    transition: all 0.15s ease;
}

.itinerary-result-card:hover {
    border-color: rgba(51, 133, 255, 0.25);
    box-shadow: 0 2px 8px rgba(51, 133, 255, 0.06);
}

.ir-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 14px;
}

.ir-icon {
    font-size: 18px;
}

.ir-title {
    font-weight: 600;
    font-size: 13px;
    color: #1A6FFF;
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.ir-badge {
    font-size: 11px;
    padding: 2px 8px;
    border-radius: 8px;
    background: rgba(51, 133, 255, 0.1);
    color: #3385FF;
    font-weight: 500;
}

.ir-budget {
    background: rgba(34, 197, 94, 0.1);
    color: #16A34A;
}

.ir-toggle {
    font-size: 10px;
    color: #94A3B8;
    margin-left: auto;
}

.ir-body {
    padding: 0 14px 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.ir-day {
    background: #fff;
    border: 1px solid rgba(51, 133, 255, 0.06);
    border-radius: 10px;
    overflow: hidden;
}

.ir-day-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: rgba(51, 133, 255, 0.03);
    border-bottom: 1px solid rgba(51, 133, 255, 0.05);
}

.ir-day-num {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: linear-gradient(135deg, #3385FF, #1A6FFF);
    color: #fff;
    font-size: 11px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
}

.ir-day-label {
    font-size: 12px;
    font-weight: 600;
    color: #1E3A5F;
}

.ir-day-count {
    font-size: 11px;
    color: #94A3B8;
    margin-left: auto;
}

.ir-items {
    padding: 8px 12px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.ir-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #5B7FA5;
    line-height: 1.5;
}

.ir-item-icon {
    font-size: 14px;
    flex-shrink: 0;
}

.ir-item-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
    color: #1E3A5F;
}

.ir-item-type {
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 6px;
    background: #F1F5F9;
    color: #7A8FA6;
    flex-shrink: 0;
}

.ir-action-btn {
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

.ir-action-btn:hover {
    background: rgba(51, 133, 255, 0.04);
    border-style: solid;
}

/* ---- 工具调用列表卡片 ---- */
.tools-result-card {
    cursor: pointer;
    background: #FAFBFC;
    border: 1px solid rgba(51, 133, 255, 0.08);
    border-radius: 12px;
    overflow: hidden;
    transition: all 0.15s ease;
}

.tools-result-card:hover {
    border-color: rgba(51, 133, 255, 0.18);
}

.tr-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
}

.tr-icon {
    font-size: 16px;
}

.tr-title {
    font-weight: 600;
    font-size: 12px;
    color: #5B7FA5;
    flex: 1;
}

.tr-toggle {
    font-size: 10px;
    color: #94A3B8;
}

.tr-body {
    padding: 0 14px 10px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.tr-tool-item {
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

.tr-tool-item:hover {
    background: #F8FAFD;
}

.tr-tool-item.tr-failed {
    opacity: 0.6;
    background: #FFF5F5;
    border-color: rgba(229, 62, 62, 0.1);
}

.tr-tool-status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex-shrink: 0;
    background: #22C55E;
}

.tr-tool-item.tr-failed .tr-tool-status-dot {
    background: #EF4444;
}

.tr-tool-icon {
    font-size: 15px;
    flex-shrink: 0;
}

.tr-tool-name {
    font-weight: 600;
    color: #1E3A5F;
    white-space: nowrap;
}

.tr-tool-args {
    color: #94A3B8;
    font-size: 11px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
}

.tr-raw-json {
    margin-top: 6px;
}

.tr-raw-summary {
    font-size: 11px;
    color: #B0C8DE;
    cursor: pointer;
    padding: 4px 0;
    user-select: none;
}

.tr-raw-summary:hover {
    color: #7A8FA6;
}

.tr-raw-pre {
    padding: 10px;
    background: rgba(0, 0, 0, 0.03);
    border-radius: 8px;
    font-size: 11px;
    line-height: 1.5;
    overflow-x: auto;
    max-height: 160px;
    overflow-y: auto;
    color: #7A8FA6;
    margin: 0;
}

/* ---- 兜底旧样式 ---- */
.tool-toggle-btn {
    font-size: 12px;
    padding: 2px 6px;
    color: #3385FF;
}

.tool-calls-expanded {
    margin-top: 8px;
}

.tool-calls-json {
    padding: 10px;
    background: rgba(0, 0, 0, 0.04);
    border-radius: 8px;
    font-size: 12px;
    line-height: 1.5;
    overflow-x: auto;
    max-height: 200px;
    overflow-y: auto;
    color: #5B7FA5;
}

/* 过渡动画 */
.ir-expand-enter-active,
.ir-expand-leave-active,
.tr-expand-enter-active,
.tr-expand-leave-active {
    transition: all 0.25s ease;
    overflow: hidden;
}

.ir-expand-enter-from,
.ir-expand-leave-to,
.tr-expand-enter-from,
.tr-expand-leave-to {
    opacity: 0;
    max-height: 0;
    padding-top: 0;
    padding-bottom: 0;
    margin-top: 0;
    margin-bottom: 0;
}

/* 系统消息区 */
.system-section {
    border-top: 1px dashed rgba(51, 133, 255, 0.1);
    padding-top: 8px;
}

.system-toggle {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    padding: 6px 0;
    user-select: none;
    color: #8BA8C8;
    font-size: 12px;
}

.system-toggle:hover {
    color: #5B7FA5;
}

.system-toggle-icon {
    font-size: 10px;
}

.system-messages {
    padding: 4px 0 4px 8px;
    border-left: 2px solid rgba(51, 133, 255, 0.08);
}

.system-msg {
    display: flex;
    align-items: baseline;
    gap: 6px;
    padding: 3px 0;
    font-size: 12px;
    color: #94A3B8;
}

.system-msg-dot {
    color: #B0C8DE;
    flex-shrink: 0;
}

.system-msg-text {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.system-msg-time {
    flex-shrink: 0;
    color: #CCDCE8;
    font-size: 10px;
}
</style>
