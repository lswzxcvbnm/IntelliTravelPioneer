<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    listConversations,
    createConversation,
    deleteConversation,
    updateConversation,
    type ConversationVO
} from '../../api/conversation'

const router = useRouter()
const conversations = ref<ConversationVO[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const sortBy = ref('update_desc')
const editDialogVisible = ref(false)
const editingConversation = ref<ConversationVO | null>(null)
const editForm = ref({ title: '', tags: '', description: '' })

// ===== 智能标题生成 =====
const DEFAULT_TITLE = '新对话'

function getDestinationFromTags(tags: string | null): string {
    if (!tags) return ''
    return tags.split(',').map(t => t.trim()).find(Boolean) || ''
}

function formatCreateDate(time: string): string {
    if (!time) return ''
    const d = new Date(time)
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hours = String(d.getHours()).padStart(2, '0')
    const minutes = String(d.getMinutes()).padStart(2, '0')
    return `${month}-${day} ${hours}:${minutes}`
}

function getDisplayTitle(conv: ConversationVO): string {
    if (conv.title && conv.title !== DEFAULT_TITLE) return conv.title
    const dest = getDestinationFromTags(conv.tags)
    if (dest) return `${dest}之旅`
    if (conv.description && conv.description.trim()) {
        const desc = conv.description.trim()
        return desc.length > 20 ? desc.slice(0, 20) + '...' : desc
    }
    if (conv.messageCount > 0) return `对话 (${conv.messageCount}条消息)`
    if (conv.createTime) return `新对话 · ${formatCreateDate(conv.createTime)}`
    return DEFAULT_TITLE
}

function getDisplayDesc(conv: ConversationVO): string {
    if (conv.description && conv.description.trim()) return conv.description
    if (conv.title === DEFAULT_TITLE && conv.messageCount > 0) {
        return '进入对话查看详情'
    }
    return ''
}

// ===== 增强搜索 =====
const displayedConversations = computed(() => {
    const kw = searchKeyword.value.trim().toLowerCase()
    if (!kw) return conversations.value
    return conversations.value.filter(conv => {
        const title = (conv.title || '').toLowerCase()
        const tags = (conv.tags || '').toLowerCase()
        const desc = (conv.description || '').toLowerCase()
        return title.includes(kw) || tags.includes(kw) || desc.includes(kw)
    })
})

const sortOptions = [
    { label: '最近更新', value: 'update_desc' },
    { label: '最早更新', value: 'update_asc' },
    { label: '最近创建', value: 'create_desc' },
    { label: '最早创建', value: 'create_asc' }
]

async function fetchConversations() {
    loading.value = true
    try {
        const sb = sortBy.value || undefined
        const res = await listConversations(undefined, sb)
        if (res.data && res.data.code === '200') {
            conversations.value = res.data.data || []
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '获取对话列表失败')
    } finally {
        loading.value = false
    }
}

async function handleCreate() {
    try {
        const res = await createConversation()
        if (res.data && res.data.code === '200' && res.data.data) {
            const conv = res.data.data
            conversations.value.unshift(conv)
            router.push(`/workspace?convId=${conv.id}`)
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '创建对话失败')
    }
}

function handleEnter(conv: ConversationVO) {
    const params = new URLSearchParams()
    params.set('convId', conv.id)
    if (conv.itineraryId) {
        params.set('itineraryId', conv.itineraryId)
    }
    router.push(`/workspace?${params.toString()}`)
}

async function handleDelete(conv: ConversationVO) {
    try {
        await ElMessageBox.confirm(
            `确定要删除对话「${getDisplayTitle(conv)}」吗？删除后对话历史将无法恢复。`,
            '确认删除',
            { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
        )
        await deleteConversation(conv.id)
        conversations.value = conversations.value.filter(c => c.id !== conv.id)
        ElMessage.success('对话已删除')
    } catch (e: any) {
        if (e !== 'cancel' && e !== 'close') {
            ElMessage.error(e?.message || '删除失败')
        }
    }
}

function openEditDialog(conv: ConversationVO) {
    editingConversation.value = conv
    editForm.value = {
        title: conv.title || '',
        tags: conv.tags || '',
        description: conv.description || ''
    }
    editDialogVisible.value = true
}

async function handleEditSave() {
    if (!editingConversation.value) return
    try {
        const res = await updateConversation(editingConversation.value.id, {
            title: editForm.value.title,
            tags: editForm.value.tags,
            description: editForm.value.description
        })
        if (res.data && res.data.code === '200' && res.data.data) {
            const updated = res.data.data
            const idx = conversations.value.findIndex(c => c.id === updated.id)
            if (idx >= 0) {
                conversations.value[idx] = updated
            }
            ElMessage.success('对话已更新')
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '更新失败')
    } finally {
        editDialogVisible.value = false
    }
}

function formatTime(time: string) {
    if (!time) return ''
    const d = new Date(time)
    const now = new Date()
    const diff = now.getTime() - d.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hours = String(d.getHours()).padStart(2, '0')
    const minutes = String(d.getMinutes()).padStart(2, '0')
    return `${month}-${day} ${hours}:${minutes}`
}

watch(sortBy, () => {
    fetchConversations()
})

onMounted(() => {
    fetchConversations()
})
</script>

<template>
    <div class="conversation-page">
        <!-- 顶部 -->
        <div class="page-header">
            <div class="header-left">
                <h1 class="page-title">AI 对话</h1>
                <p class="page-subtitle">管理你的旅行规划对话，随时继续未完成的行程</p>
            </div>
            <button class="btn-create" @click="handleCreate">
                <span class="btn-create-icon">+</span>
                新建对话
            </button>
        </div>

        <!-- 工具栏 -->
        <div class="toolbar">
            <div class="search-box">
                <span class="search-icon">🔍</span>
                <input
                    v-model="searchKeyword"
                    type="text"
                    class="search-input"
                    placeholder="搜索对话标题、标签、描述..."
                />
                <span v-if="searchKeyword" class="search-clear" @click="searchKeyword = ''">✕</span>
            </div>
            <select v-model="sortBy" class="sort-select">
                <option v-for="opt in sortOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                </option>
            </select>
        </div>

        <!-- 对话卡片列表 -->
        <div v-loading="loading" class="conversation-grid">
            <div
                v-for="conv in displayedConversations"
                :key="conv.id"
                class="conv-card"
                @click="handleEnter(conv)"
            >
                <!-- 卡片头部 -->
                <div class="conv-card-top">
                    <div class="conv-title-row">
                        <span v-if="getDestinationFromTags(conv.tags)" class="conv-dest-icon">📍</span>
                        <h3 class="conv-title" :class="{ 'is-default': conv.title === DEFAULT_TITLE && !getDestinationFromTags(conv.tags) }">
                            {{ getDisplayTitle(conv) }}
                        </h3>
                    </div>
                    <span v-if="conv.itineraryId" class="conv-itinerary-badge">已有行程</span>
                </div>

                <!-- 描述 -->
                <p v-if="getDisplayDesc(conv)" class="conv-desc">{{ getDisplayDesc(conv) }}</p>
                <p v-else class="conv-desc is-empty">暂无描述</p>

                <!-- 标签行 -->
                <div v-if="conv.tags" class="conv-tags">
                    <span
                        v-for="tag in conv.tags.split(',').filter(Boolean)"
                        :key="tag"
                        class="conv-tag"
                    >{{ tag.trim() }}</span>
                </div>

                <!-- 底部操作栏 -->
                <div class="conv-card-footer">
                    <div class="conv-stats">
                        <span class="conv-stat">💬 {{ conv.messageCount }} 条消息</span>
                        <span class="conv-stat">🕐 {{ formatTime(conv.updateTime) }}</span>
                    </div>
                    <div class="conv-actions" @click.stop>
                        <button class="conv-action-btn" title="编辑对话" @click="openEditDialog(conv)">
                            编辑
                        </button>
                        <button 
                            v-if="!conv.itineraryId" 
                            class="conv-action-btn is-danger" 
                            title="删除对话" 
                            @click="handleDelete(conv)"
                        >
                            删除
                        </button>
                        <span v-else class="conv-action-hint" title="有关联行程，请在行程管理中删除">
                            🔒
                        </span>
                    </div>
                </div>
            </div>

            <!-- 空状态 -->
            <div v-if="!loading && displayedConversations.length === 0" class="empty-state">
                <div class="empty-icon">💬</div>
                <p class="empty-title">{{ searchKeyword.trim() ? '未找到匹配的对话' : '还没有对话记录' }}</p>
                <p class="empty-desc">
                    {{ searchKeyword.trim() ? '试试调整搜索关键词' : '点击右上角「新建对话」开始你的第一次 AI 旅行规划' }}
                </p>
                <button v-if="!searchKeyword.trim()" class="empty-create-btn" @click="handleCreate">
                    开始新对话
                </button>
            </div>
        </div>

        <!-- 编辑弹窗 -->
        <el-dialog v-model="editDialogVisible" title="编辑对话" width="480px">
            <el-form :model="editForm" label-position="top">
                <el-form-item label="对话标题">
                    <el-input v-model="editForm.title" placeholder="输入对话标题" />
                </el-form-item>
                <el-form-item label="标签">
                    <el-input v-model="editForm.tags" placeholder="多个标签用逗号分隔" />
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
    </div>
</template>

<style scoped>
.conversation-page {
    max-width: 1080px;
    margin: 0 auto;
    padding: 36px 28px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif;
}

/* ===== 页面头部 ===== */
.page-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 28px;
}
.header-left {
    display: flex;
    flex-direction: column;
    gap: 6px;
}
.page-title {
    font-size: 28px;
    font-weight: 800;
    color: #1A1A1A;
    margin: 0;
    letter-spacing: -0.5px;
}
.page-subtitle {
    font-size: 15px;
    color: #606266;
    margin: 0;
    font-weight: 500;
}

/* 新建按钮 */
.btn-create {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 14px 28px;
    border: none;
    border-radius: 10px;
    background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
    color: #fff;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: all 0.2s ease;
    flex-shrink: 0;
    font-family: inherit;
    outline: none;
    box-shadow: 0 4px 12px rgba(51, 133, 255, 0.3);
}
.btn-create:hover { 
    background: linear-gradient(135deg, #1A6FFF 0%, #0052CC 100%);
    box-shadow: 0 6px 16px rgba(51, 133, 255, 0.4);
    transform: translateY(-1px);
}
.btn-create-icon {
    font-size: 20px;
    font-weight: 400;
    line-height: 1;
}

/* ===== 工具栏 ===== */
.toolbar {
    display: flex;
    gap: 12px;
    margin-bottom: 24px;
}

.search-box {
    flex: 1;
    display: flex;
    align-items: center;
    background: #fff;
    border: 2px solid #E4E7ED;
    border-radius: 10px;
    padding: 0 16px;
    transition: all 0.2s ease;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
}
.search-box:focus-within {
    border-color: #3385FF;
    box-shadow: 0 2px 8px rgba(51, 133, 255, 0.1);
}
.search-icon {
    font-size: 16px;
    flex-shrink: 0;
    margin-right: 8px;
    opacity: 0.5;
}
.search-input {
    flex: 1;
    border: none;
    outline: none;
    font-size: 14px;
    color: #303133;
    background: transparent;
    padding: 10px 0;
    font-family: inherit;
}
.search-input::placeholder { color: #C0C4CC; }
.search-clear {
    font-size: 14px;
    color: #C0C4CC;
    cursor: pointer;
    padding: 4px;
    flex-shrink: 0;
}
.search-clear:hover { color: #909399; }

.sort-select {
    width: 160px;
    padding: 10px 14px;
    border: 2px solid #E4E7ED;
    border-radius: 10px;
    font-size: 14px;
    color: #303133;
    font-weight: 500;
    background: #fff;
    outline: none;
    cursor: pointer;
    font-family: inherit;
    transition: all 0.2s ease;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
}
.sort-select:focus { 
    border-color: #3385FF;
    box-shadow: 0 2px 8px rgba(51, 133, 255, 0.1);
}

/* ===== 卡片网格 ===== */
.conversation-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 18px;
}

/* ===== 对话卡片 ===== */
.conv-card {
    background: #fff;
    border-radius: 12px;
    padding: 20px;
    border: 2px solid #E4E7ED;
    cursor: pointer;
    transition: all 0.2s ease;
    display: flex;
    flex-direction: column;
    gap: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.conv-card:hover {
    border-color: #3385FF;
    box-shadow: 0 4px 16px rgba(51, 133, 255, 0.12);
    transform: translateY(-2px);
}

/* 卡片顶部 */
.conv-card-top {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px;
}
.conv-title-row {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    min-width: 0;
}
.conv-dest-icon {
    font-size: 16px;
    flex-shrink: 0;
}
.conv-title {
    font-size: 17px;
    font-weight: 700;
    color: #000000;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    letter-spacing: -0.2px;
    line-height: 1.4;
}
.conv-title.is-default {
    color: #000000;
    font-weight: 500;
}

/* 已有行程徽章 */
.conv-itinerary-badge {
    padding: 4px 12px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #1890FF;
    background: #E6F7FF;
    border: 1px solid #91D5FF;
    flex-shrink: 0;
    white-space: nowrap;
}

/* 描述 */
.conv-desc {
  font-size: 14px;
  color: #4A4A4A;
  margin: 0;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 22px;
  font-weight: 500;
}
.conv-desc.is-empty {
    color: #A8ABB2;
}

/* 标签 */
.conv-tags {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}
.conv-tag {
    display: inline-block;
    padding: 4px 12px;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    color: #1A6FFF;
    background: #E8F0FE;
    border: 1px solid #C6D6F0;
}

/* 底部操作栏 */
.conv-card-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 4px;
    border-top: 1px solid #F2F3F5;
}
.conv-stats {
    display: flex;
    align-items: center;
    gap: 14px;
}
.conv-stat {
    font-size: 13px;
    color: #606266;
    font-weight: 500;
}
.conv-actions {
    display: flex;
    gap: 8px;
}
.conv-action-btn {
    padding: 6px 14px;
    border: 1px solid #D9ECFF;
    border-radius: 6px;
    background: #fff;
    color: #1A6FFF;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
    outline: none;
}
.conv-action-btn:hover {
    border-color: #3385FF;
    color: #3385FF;
    background: #F0F7FF;
    box-shadow: 0 2px 6px rgba(51, 133, 255, 0.15);
}
.conv-action-btn.is-danger:hover {
    border-color: #F56C6C;
    color: #F56C6C;
    background: #FEF0F0;
    box-shadow: 0 2px 6px rgba(245, 108, 108, 0.15);
}
.conv-action-hint {
    font-size: 12px;
    cursor: help;
    opacity: 0.6;
}

/* ===== 空状态 ===== */
.empty-state {
    grid-column: 1 / -1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 80px 24px;
    background: #fff;
    border-radius: 12px;
    border: 2px solid #E4E7ED;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.empty-icon {
    font-size: 48px;
    margin-bottom: 16px;
}
.empty-title {
    font-size: 18px;
    font-weight: 700;
    color: #303133;
    margin: 0 0 8px;
}
.empty-desc {
    font-size: 14px;
    color: #606266;
    margin: 0 0 24px;
    text-align: center;
    line-height: 1.6;
    font-weight: 500;
}
.empty-create-btn {
    padding: 12px 28px;
    border: none;
    border-radius: 8px;
    background: linear-gradient(135deg, #3385FF 0%, #1A6FFF 100%);
    color: #fff;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    font-family: inherit;
    transition: all 0.2s ease;
    outline: none;
    box-shadow: 0 4px 12px rgba(51, 133, 255, 0.3);
}
.empty-create-btn:hover { 
    background: linear-gradient(135deg, #1A6FFF 0%, #0052CC 100%);
    box-shadow: 0 6px 16px rgba(51, 133, 255, 0.4);
    transform: translateY(-1px);
}
</style>
