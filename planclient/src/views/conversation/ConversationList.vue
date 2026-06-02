<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
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

const sortOptions = [
    { label: '最近更新', value: 'update_desc' },
    { label: '最早更新', value: 'update_asc' },
    { label: '最近创建', value: 'create_desc' },
    { label: '最早创建', value: 'create_asc' }
]

async function fetchConversations() {
    loading.value = true
    try {
        const kw = searchKeyword.value.trim() || undefined
        const sb = sortBy.value || undefined
        const res = await listConversations(kw, sb)
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
            router.push(`/conversation/${conv.id}`)
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '创建对话失败')
    }
}

function handleEnter(conv: ConversationVO) {
    router.push(`/conversation/${conv.id}`)
}

async function handleDelete(conv: ConversationVO) {
    try {
        await ElMessageBox.confirm(
            `确定要删除对话「${conv.title}」吗？删除后对话历史将无法恢复。`,
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

watch([searchKeyword, sortBy], () => {
    fetchConversations()
})

onMounted(() => {
    fetchConversations()
})
</script>

<template>
    <div class="conversation-page">
        <div class="page-header">
            <div class="header-left">
                <h1 class="page-title">AI 对话</h1>
                <span class="page-subtitle">管理你的 AI 旅行规划对话</span>
            </div>
            <el-button type="primary" size="large" @click="handleCreate" class="btn-create-conv">
                <span style="font-size: 18px; margin-right: 4px;">+</span>
                新建对话
            </el-button>
        </div>

        <div class="toolbar">
            <el-input
                v-model="searchKeyword"
                placeholder="搜索对话标题..."
                clearable
                :prefix-icon="null"
                class="search-input"
            >
                <template #prepend>
                    <span style="font-size: 16px;">🔍</span>
                </template>
            </el-input>
            <el-select v-model="sortBy" class="sort-select">
                <el-option
                    v-for="opt in sortOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                />
            </el-select>
        </div>

        <div class="conversation-grid" v-loading="loading">
            <div
                v-for="conv in conversations"
                :key="conv.id"
                class="conv-card"
                @click="handleEnter(conv)"
            >
                <div class="conv-card-header">
                    <h3 class="conv-title">{{ conv.title }}</h3>
                    <div class="conv-actions" @click.stop>
                        <el-button text size="small" @click="openEditDialog(conv)" title="编辑">
                            ✏️
                        </el-button>
                        <el-button text size="small" @click="handleDelete(conv)" title="删除">
                            🗑️
                        </el-button>
                    </div>
                </div>
                <p v-if="conv.description" class="conv-desc">{{ conv.description }}</p>
                <p v-else class="conv-desc placeholder">暂无描述</p>
                <div class="conv-meta">
                    <el-tag
                        v-if="conv.tags"
                        v-for="tag in conv.tags.split(',').filter(Boolean)"
                        :key="tag"
                        size="small"
                        type="info"
                        class="conv-tag"
                    >
                        {{ tag.trim() }}
                    </el-tag>
                    <span class="conv-msg-count">{{ conv.messageCount }} 条消息</span>
                    <span class="conv-time">{{ formatTime(conv.updateTime) }}</span>
                </div>
            </div>

            <div v-if="!loading && conversations.length === 0" class="empty-state">
                <div class="empty-icon">💬</div>
                <p class="empty-text">暂无对话</p>
                <p class="empty-hint">点击「新建对话」开始与 AI 交流</p>
            </div>
        </div>

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
    max-width: 960px;
    margin: 0 auto;
    padding: 32px 24px;
}

.page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 28px;
}

.header-left {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.page-title {
    font-size: 26px;
    font-weight: 700;
    color: #1E3A5F;
    margin: 0;
}

.page-subtitle {
    font-size: 14px;
    color: #7B9CC0;
}

.btn-create-conv {
    border-radius: 12px;
    padding: 14px 28px;
    font-size: 15px;
    font-weight: 600;
    background: linear-gradient(135deg, #3385FF, #1A6FFF);
    border: none;
    box-shadow: 0 4px 14px rgba(51, 133, 255, 0.3);
    transition: all 0.2s ease;
}

.btn-create-conv:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(51, 133, 255, 0.4);
    background: linear-gradient(135deg, #4D94FF, #2A7FFF);
}

.toolbar {
    display: flex;
    gap: 12px;
    margin-bottom: 24px;
}

.search-input {
    flex: 1;
}

.search-input :deep(.el-input__wrapper) {
    border-radius: 10px;
}

.sort-select {
    width: 160px;
}

.sort-select :deep(.el-input__wrapper) {
    border-radius: 10px;
}

.conversation-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
}

.conv-card {
    background: #fff;
    border-radius: 14px;
    padding: 20px;
    border: 1px solid rgba(51, 133, 255, 0.08);
    cursor: pointer;
    transition: all 0.2s ease;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.conv-card:hover {
    border-color: rgba(51, 133, 255, 0.2);
    box-shadow: 0 4px 16px rgba(51, 133, 255, 0.1);
    transform: translateY(-2px);
}

.conv-card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 8px;
}

.conv-title {
    font-size: 16px;
    font-weight: 600;
    color: #1E3A5F;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    margin-right: 8px;
}

.conv-actions {
    display: flex;
    gap: 2px;
    opacity: 0;
    transition: opacity 0.15s;
}

.conv-card:hover .conv-actions {
    opacity: 1;
}

.conv-desc {
    font-size: 13px;
    color: #5B7FA5;
    margin: 0 0 12px 0;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    min-height: 36px;
}

.conv-desc.placeholder {
    color: #B0C8DE;
    font-style: italic;
}

.conv-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.conv-tag {
    border-radius: 6px;
}

.conv-msg-count {
    font-size: 12px;
    color: #8BA8C8;
    margin-left: auto;
}

.conv-time {
    font-size: 12px;
    color: #B0C8DE;
}

.empty-state {
    grid-column: 1 / -1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 80px 24px;
    background: #fff;
    border-radius: 14px;
    border: 1px dashed rgba(51, 133, 255, 0.15);
}

.empty-icon {
    font-size: 48px;
    margin-bottom: 16px;
}

.empty-text {
    font-size: 16px;
    font-weight: 600;
    color: #5B7FA5;
    margin: 0 0 6px 0;
}

.empty-hint {
    font-size: 13px;
    color: #B0C8DE;
    margin: 0;
}
</style>
