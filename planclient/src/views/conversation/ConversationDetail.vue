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
        return `${days}天行程 · 约¥${cost}/人`
    }
    return '查看工具调用详情'
}

function goToItinerary() {
    if (conversation.value?.itineraryId) {
        router.push(`/battle/create?itineraryId=${conversation.value.itineraryId}`)
    } else {
        router.push('/battle/create')
    }
}

function formatPlanPreview(plan: any): string {
    if (!plan?.dailyPlans) return ''
    const lines: string[] = []
    for (const day of plan.dailyPlans) {
        const names = (day.items || []).map((i: any) => i.name).filter(Boolean).join(' → ')
        if (names) {
            lines.push(`📅 Day ${day.dayNumber}：${names}`)
        }
    }
    if (plan.estimatedCost) {
        lines.push(`💰 预估 ¥${plan.estimatedCost}/人`)
    }
    return lines.join('\n')
}

function goNewPlan() {
    router.push('/battle/create')
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
                ← 返回
            </el-button>
            <div class="chat-header-title">
                <h2 class="chat-title">{{ conversation?.title || '对话详情' }}</h2>
                <span v-if="conversation?.description" class="chat-desc">{{ conversation.description }}</span>
            </div>
            <div class="chat-header-actions">
                <el-button
                    v-if="conversation?.itineraryId"
                    type="primary"
                    size="small"
                    @click="goToItinerary"
                    class="action-btn"
                >
                    📋 查看行程
                </el-button>
                <el-button text size="small" @click="openEditDialog" class="edit-btn" title="编辑对话信息">
                    ✏️ 编辑
                </el-button>
                <el-button size="small" @click="goNewPlan" class="action-btn-outline">
                    ＋ 新规划
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
                            <div v-if="msg.toolCalls" class="message-tool-calls">
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
                                    <pre v-if="parseToolCalls(msg.toolCalls)?.type === 'tool'" class="tool-calls-json">{{
                                        JSON.stringify(parseToolCalls(msg.toolCalls)?.data, null, 2)
                                    }}</pre>
                                    <div v-else-if="parseToolCalls(msg.toolCalls)?.type === 'itinerary'" class="plan-card">
                                        <pre class="plan-preview">{{ formatPlanPreview(parseToolCalls(msg.toolCalls)?.data) }}</pre>
                                    </div>
                                </div>
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
                            <span class="system-msg-dot">·</span>
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
    max-width: 800px;
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
    max-width: 75%;
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

.message-tool-calls {
    margin-top: 10px;
    border-top: 1px solid rgba(255, 255, 255, 0.2);
    padding-top: 8px;
}

.bubble-assistant .message-tool-calls {
    border-top-color: rgba(51, 133, 255, 0.1);
}

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

.plan-card {
    padding: 12px;
    background: rgba(51, 133, 255, 0.04);
    border-radius: 8px;
    border: 1px solid rgba(51, 133, 255, 0.1);
}

.plan-preview {
    font-size: 13px;
    line-height: 1.7;
    color: #1E3A5F;
    white-space: pre-wrap;
    margin: 0;
    font-family: inherit;
}

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
    color: #8BA8C8;
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
