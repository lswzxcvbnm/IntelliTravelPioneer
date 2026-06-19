<template>
  <div class="skill-page">
    <div class="skill-header">
      <div>
        <h1>🧩 技能工作台</h1>
        <p class="subtitle">管理你的 Agent 专属技能，为行程规划注入个性化能力</p>
      </div>
      <div class="header-actions">
        <button class="btn-outline" @click="activeTab = 'public'">
          <el-icon><Collection /></el-icon>
          <span>技能市场</span>
        </button>
        <button class="btn-create" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          <span>创建技能</span>
        </button>
      </div>
    </div>

    <div class="tab-bar">
      <button class="tab-item" :class="{ active: activeTab === 'mine' }" @click="activeTab = 'mine'">
        我的技能
      </button>
      <button class="tab-item" :class="{ active: activeTab === 'public' }" @click="activeTab = 'public'">
        技能市场
      </button>
    </div>

    <div v-if="loading" class="skeleton-list">
      <div v-for="i in 3" :key="i" class="skeleton-row">
        <div class="skel-title"></div>
        <div class="skel-desc"></div>
        <div class="skel-meta"></div>
      </div>
    </div>

    <div v-else-if="currentList.length === 0" class="empty-page">
      <el-icon :size="48" class="empty-icon"><Collection /></el-icon>
      <p class="empty-title">{{ activeTab === 'mine' ? '还没有创建技能' : '技能市场暂无公开技能' }}</p>
      <p class="empty-desc" v-if="activeTab === 'mine'">点击右上角"创建技能"来定义你的第一个 Agent 专属技能</p>
    </div>

    <div v-else class="skill-grid">
      <div v-for="(skill, index) in currentList" :key="skill.id" class="skill-card" :style="{ animationDelay: `${index * 0.05}s` }">
        <div class="skill-card-header">
          <span class="skill-name">{{ skill.name }}</span>
          <span class="skill-badge" v-if="skill.isPublic">公开</span>
          <span class="skill-badge private" v-else>私有</span>
        </div>
        <p class="skill-desc">{{ skill.description || '暂无描述' }}</p>
        <p class="skill-prompt-preview">{{ truncateText(skill.prompt, 120) }}</p>
        <div class="skill-resources" v-if="getRefCount(skill) > 0 || getScriptCount(skill) > 0">
          <span class="tag" v-if="getRefCount(skill) > 0">{{ getRefCount(skill) }} 份参考</span>
          <span class="tag" v-if="getScriptCount(skill) > 0">{{ getScriptCount(skill) }} 个脚本</span>
        </div>
        <div class="skill-card-footer">
          <span class="skill-time">{{ formatTime(skill.createTime) }}</span>
          <div class="skill-actions" v-if="activeTab === 'mine'">
            <button class="btn-text" @click="showEditDialog(skill)">编辑</button>
            <button class="btn-text danger" @click="confirmDelete(skill)">删除</button>
          </div>
          <div class="skill-actions" v-else>
            <button class="btn-text copy" @click="copyPublicSkill(skill)">复制到我的技能</button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingSkill ? '编辑技能' : '创建新技能'"
      width="540px"
      :close-on-click-modal="false"
    >
      <div class="skill-form">
        <div class="form-group">
          <label>技能名称 <span class="required">*</span></label>
          <input v-model="form.name" placeholder="例如：摄影旅拍助手" class="form-input" maxlength="50" />
        </div>
        <div class="form-group">
          <label>技能描述</label>
          <input v-model="form.description" placeholder="一句话描述这个技能做什么" class="form-input" maxlength="200" />
        </div>
        <div class="form-group">
          <label>技能提示词 <span class="required">*</span></label>
          <textarea v-model="form.prompt" rows="6" class="form-input form-textarea"
            placeholder="详细的指令/提示词，告诉Agent在规划行程时应该如何运用这个技能。例如：
在每天的行程中至少安排1-2个适合拍照的打卡点，优先推荐日落时分光线好的位置，附带简要拍摄Tips。" />
        </div>
        <div class="form-group inline">
          <label class="switch-label">
            <input type="checkbox" v-model="form.isPublic" class="switch-input" />
            <span>公开到技能市场（其他用户也可以使用此技能）</span>
          </label>
        </div>

        <!-- References section -->
        <div class="form-section">
          <div class="section-header" @click="showRefs = !showRefs">
            <span>📚 参考资料</span>
            <span class="section-toggle">{{ showRefs ? '收起' : '展开' }}</span>
          </div>
          <div v-if="showRefs" class="section-body">
            <p class="section-tip">添加Markdown格式的参考文档，Agent规划行程时可按需加载参考内容。</p>
            <div v-for="(ref, i) in form.references" :key="'ref-'+i" class="kv-row">
              <el-input v-model="ref.key" placeholder="文件名，如 北京美食指南.md" size="small" class="kv-key" />
              <el-input v-model="ref.value" type="textarea" placeholder="文档内容..." size="small" class="kv-val" rows="3" />
              <button class="btn-remove-kv" @click="removeRef(i)" title="删除">✕</button>
            </div>
            <button class="btn-add-kv" @click="addRef">+ 添加参考资料</button>
          </div>
        </div>

        <!-- Scripts section -->
        <div class="form-section">
          <div class="section-header" @click="showScripts = !showScripts">
            <span>⚙️ 可执行脚本</span>
            <span class="section-toggle">{{ showScripts ? '收起' : '展开' }}</span>
          </div>
          <div v-if="showScripts" class="section-body">
            <p class="section-tip">添加脚本代码，Agent执行时可引用（如数据清洗脚本、格式转换脚本等）。</p>
            <div v-for="(script, i) in form.scripts" :key="'script-'+i" class="kv-row">
              <el-input v-model="script.key" placeholder="脚本名，如 parse_gpx.py" size="small" class="kv-key" />
              <el-input v-model="script.value" type="textarea" placeholder="脚本代码..." size="small" class="kv-val" rows="4" />
              <button class="btn-remove-kv" @click="removeScript(i)" title="删除">✕</button>
            </div>
            <button class="btn-add-kv" @click="addScript">+ 添加脚本</button>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn-cancel" @click="dialogVisible = false">取消</button>
        <button class="btn-submit" @click="submitSkill" :disabled="!form.name || !form.prompt || submitting">
          {{ submitting ? '保存中...' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Collection } from '@element-plus/icons-vue'
import { listMySkills, listPublicSkills, createSkill, updateSkill, deleteSkill, type SkillVO, type SkillSaveRequest } from '../../api/skill'

const activeTab = ref<'mine' | 'public'>('mine')
const loading = ref(false)
const mySkills = ref<SkillVO[]>([])
const publicSkills = ref<SkillVO[]>([])

const dialogVisible = ref(false)
const editingSkill = ref<SkillVO | null>(null)
const submitting = ref(false)
const showRefs = ref(false)
const showScripts = ref(false)

interface KVItem { key: string; value: string }

interface SkillForm {
  name: string
  description: string
  prompt: string
  isPublic: boolean
  references: KVItem[]
  scripts: KVItem[]
}

const form = ref<SkillForm>({
  name: '',
  description: '',
  prompt: '',
  isPublic: false,
  references: [],
  scripts: []
})

const currentList = computed(() => activeTab.value === 'mine' ? mySkills.value : publicSkills.value)

async function loadSkills() {
  loading.value = true
  try {
    const [mineR, publicR] = await Promise.all([
      listMySkills().catch(() => ({ data: [] })),
      listPublicSkills().catch(() => ({ data: [] }))
    ])
    mySkills.value = mineR.data || []
    publicSkills.value = publicR.data || []
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  editingSkill.value = null
  form.value = { name: '', description: '', prompt: '', isPublic: false, references: [], scripts: [] }
  showRefs.value = false
  showScripts.value = false
  dialogVisible.value = true
}

function showEditDialog(skill: SkillVO) {
  editingSkill.value = skill
  form.value = {
    name: skill.name,
    description: skill.description || '',
    prompt: skill.prompt,
    isPublic: skill.isPublic,
    references: mapToKV(skill.references),
    scripts: mapToKV(skill.scripts)
  }
  showRefs.value = getRefCount(skill) > 0
  showScripts.value = getScriptCount(skill) > 0
  dialogVisible.value = true
}

async function submitSkill() {
  if (!form.value.name || !form.value.prompt) return
  submitting.value = true
  try {
    const payload: SkillSaveRequest = {
      name: form.value.name,
      description: form.value.description,
      prompt: form.value.prompt,
      isPublic: form.value.isPublic,
      references: kvToMap(form.value.references),
      scripts: kvToMap(form.value.scripts)
    }
    if (editingSkill.value) {
      await updateSkill(editingSkill.value.id, payload)
      ElMessage.success('技能已更新')
    } else {
      await createSkill(payload)
      ElMessage.success('技能已创建')
    }
    dialogVisible.value = false
    await loadSkills()
  } catch {
    ElMessage.error('操作失败，请重试')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete(skill: SkillVO) {
  try {
    await ElMessageBox.confirm(`确定要删除技能「${skill.name}」吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSkill(skill.id)
    ElMessage.success('已删除')
    await loadSkills()
  } catch { /* 取消操作 */ }
}

function truncateText(text: string, max: number): string {
  if (!text) return ''
  return text.length > max ? text.substring(0, max) + '...' : text
}

function formatTime(time: string): string {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function getRefCount(skill: SkillVO): number {
  return skill.references ? Object.keys(skill.references).length : 0
}

function getScriptCount(skill: SkillVO): number {
  return skill.scripts ? Object.keys(skill.scripts).length : 0
}

function mapToKV(map: Record<string, string> | null | undefined): KVItem[] {
  if (!map) return []
  return Object.entries(map).map(([k, v]) => ({ key: k, value: v }))
}

function kvToMap(kv: KVItem[]): Record<string, string> {
  const map: Record<string, string> = {}
  for (const item of kv) {
    if (item.key.trim()) {
      map[item.key.trim()] = item.value
    }
  }
  return map
}

function addRef() {
  form.value.references.push({ key: '', value: '' })
  showRefs.value = true
}

function removeRef(i: number) {
  form.value.references.splice(i, 1)
}

function addScript() {
  form.value.scripts.push({ key: '', value: '' })
  showScripts.value = true
}

function removeScript(i: number) {
  form.value.scripts.splice(i, 1)
}

async function copyPublicSkill(skill: SkillVO) {
  try {
    await createSkill({
      name: skill.name,
      description: skill.description || '',
      prompt: skill.prompt,
      isPublic: false,
      references: skill.references || {},
      scripts: skill.scripts || {},
    })
    ElMessage.success(`已复制「${skill.name}」到我的技能`)
    await loadSkills()
    activeTab.value = 'mine'
  } catch {
    ElMessage.error('复制失败，请重试')
  }
}

onMounted(() => { loadSkills() })
</script>

<style scoped>
.skill-page { max-width: 960px; margin: 0 auto; padding: 32px 20px; }
.skill-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.skill-header h1 { font-size: 24px; font-weight: 700; color: #1a1a2e; margin: 0; }
.subtitle { font-size: 14px; color: #909399; margin: 6px 0 0; }
.header-actions { display: flex; gap: 10px; }
.btn-outline {
  display: flex; align-items: center; gap: 5px;
  padding: 9px 18px; border: 1px solid #c0c4cc; border-radius: 10px;
  background: #fff; color: #606266; font-size: 14px; cursor: pointer;
  transition: all .2s;
  outline: none;
}
.btn-outline:hover { border-color: #8AC4FF; color: #3385FF; }
.btn-outline:focus { outline: none; box-shadow: none; }
.btn-create {
  display: flex; align-items: center; gap: 5px;
  padding: 9px 20px; border: none; border-radius: 10px;
  background: linear-gradient(135deg, #8AC4FF, #3385FF);
  color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
  transition: opacity .2s;
  outline: none;
}
.btn-create:hover { opacity: .9; }
.btn-create:focus { outline: none; box-shadow: none; }

.tab-bar { display: flex; gap: 0; margin-bottom: 24px; border-bottom: 2px solid #ebeef5; }
.tab-item {
  padding: 10px 24px; border: none; background: none;
  font-size: 15px; color: #909399; cursor: pointer;
  border-bottom: 2px solid transparent; margin-bottom: -2px;
  transition: all .2s;
  outline: none;
}
.tab-item.active { color: #3385FF; border-bottom-color: #3385FF; font-weight: 600; }
.tab-item:hover { color: #3385FF; }
.tab-item:focus { outline: none; box-shadow: none; }

.skill-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; width: 100%; }
.skill-card {
  background: #fff; border-radius: 14px; padding: 18px 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,.04); transition: box-shadow .2s, transform .2s;
  animation: cardFadeIn .3s ease both;
}
.skill-card:hover { box-shadow: 0 4px 20px rgba(0,0,0,.08); transform: translateY(-2px); }
@keyframes cardFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.skill-card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.skill-name { font-size: 16px; font-weight: 700; color: #1a1a2e; }
.skill-badge {
  font-size: 11px; padding: 2px 8px; border-radius: 6px;
  background: #e1f3d8; color: #529b2e;
}
.skill-badge.private { background: #f0f0f0; color: #909399; }
.skill-desc { font-size: 13px; color: #606266; margin: 0 0 8px; line-height: 1.5; }
.skill-prompt-preview { font-size: 12px; color: #909399; margin: 0 0 12px; line-height: 1.6; background: #f8f9fa; padding: 8px 10px; border-radius: 6px; }
.skill-card-footer { display: flex; justify-content: space-between; align-items: center; }
.skill-time { font-size: 12px; color: #c0c4cc; }
.skill-actions { display: flex; gap: 8px; }
.btn-text { border: none; background: none; font-size: 13px; color: #3385FF; cursor: pointer; padding: 0; outline: none; }
.btn-text:hover { text-decoration: underline; }
.btn-text:focus { outline: none; box-shadow: none; }
.btn-text.danger { color: #f56c6c; }
.btn-text.copy { color: #7c3aed; }

.skill-form { display: flex; flex-direction: column; gap: 16px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-group.inline { flex-direction: row; }
.form-group label { font-size: 14px; font-weight: 600; color: #303133; }
.required { color: #f56c6c; }
.form-input {
  padding: 10px 14px; border: 1px solid #dcdfe6; border-radius: 8px;
  font-size: 14px; outline: none; transition: border-color .2s;
  color: #303133; background: #fff;
}
.form-input:focus { border-color: #8AC4FF; }
.form-textarea { resize: vertical; min-height: 120px; line-height: 1.6; }
.switch-label { display: flex; align-items: center; gap: 8px; font-weight: 400; cursor: pointer; }
.switch-input { width: 16px; height: 16px; cursor: pointer; accent-color: #3385FF; }

.btn-cancel {
  padding: 9px 20px; border: 1px solid #dcdfe6; border-radius: 8px;
  background: #fff; color: #606266; font-size: 14px; cursor: pointer;
  outline: none;
}
.btn-cancel:focus { outline: none; box-shadow: none; }
.btn-submit {
  padding: 9px 24px; border: none; border-radius: 8px;
  background: linear-gradient(135deg, #8AC4FF, #3385FF);
  color: #fff; font-size: 14px; font-weight: 600; cursor: pointer;
  margin-left: 10px;
  outline: none;
}
.btn-submit:focus { outline: none; box-shadow: none; }
.btn-submit:disabled { opacity: .5; cursor: not-allowed; }

.skeleton-list { display: flex; flex-direction: column; gap: 12px; }
.skeleton-row { padding: 18px 20px; border-radius: 14px; background: #f5f7fa; }
.skel-title { height: 18px; width: 40%; background: #e4e7ed; border-radius: 4px; margin-bottom: 8px; }
.skel-desc { height: 14px; width: 70%; background: #e4e7ed; border-radius: 4px; margin-bottom: 8px; }
.skel-meta { height: 12px; width: 30%; background: #e4e7ed; border-radius: 4px; }

.empty-page { text-align: center; padding: 60px 0; color: #909399; }
.empty-icon { margin-bottom: 12px; }
.empty-title { font-size: 16px; font-weight: 600; margin: 0 0 6px; color: #606266; }
.empty-desc { font-size: 13px; margin: 0; }



/* Resources tags */
.skill-resources { display: flex; gap: 6px; margin-bottom: 10px; }
.tag {
  font-size: 11px; padding: 2px 8px; border-radius: 4px;
  background: #ecf5ff; color: #3385FF;
}

/* References & Scripts sections */
.form-section {
  border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden;
}
.section-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; background: #f8f9fa; cursor: pointer;
  font-size: 14px; font-weight: 600; color: #303133;
  transition: background .2s;
}
.section-header:hover { background: #f0f2f5; }
.section-toggle { font-size: 12px; color: #909399; font-weight: 400; }
.section-body { padding: 12px 14px; }
.section-tip { font-size: 12px; color: #909399; margin: 0 0 10px; }
.kv-row { display: flex; gap: 8px; align-items: flex-start; margin-bottom: 8px; }
.kv-key { width: 160px; min-width: 140px; }
.kv-val { flex: 1; }
.btn-remove-kv {
  border: none; background: none; color: #f56c6c; font-size: 16px;
  cursor: pointer; padding: 4px 6px; line-height: 1;
  outline: none;
}
.btn-remove-kv:hover { opacity: .7; }
.btn-remove-kv:focus { outline: none; box-shadow: none; }
.btn-add-kv {
  border: 1px dashed #c0c4cc; border-radius: 6px; background: none;
  padding: 6px 14px; font-size: 13px; color: #909399; cursor: pointer;
  width: 100%; text-align: center; transition: all .2s;
  outline: none;
}
.btn-add-kv:hover { border-color: #8AC4FF; color: #3385FF; }
.btn-add-kv:focus { outline: none; box-shadow: none; }
</style>
