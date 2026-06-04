import { ref, nextTick } from 'vue'

export interface WsMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: Date
  toolCalls?: string | null
  streaming?: boolean
}

export function useConversation() {
  const messages = ref<WsMessage[]>([])
  const messagesRef = ref<HTMLElement | null>(null)

  // 添加用户消息
  function addUserMessage(content: string): string {
    const id = Date.now().toString()
    messages.value.push({
      id,
      role: 'user',
      content,
      time: new Date(),
    })
    return id
  }

  // 添加助手消息占位
  function addAssistantMessage(): string {
    const id = (Date.now() + 1).toString()
    messages.value.push({
      id,
      role: 'assistant',
      content: '',
      time: new Date(),
      streaming: true,
    })
    return id
  }

  // 更新消息
  function updateMessage(msgId: string, patch: Partial<WsMessage>) {
    const msg = messages.value.find(m => m.id === msgId)
    if (!msg) return
    
    // 防止对象型内容被直接赋值
    if ('content' in patch && typeof patch.content !== 'string') {
      patch.content = patch.content == null ? '' : String(patch.content)
    }
    Object.assign(msg, patch)
  }

  // 追加消息内容
  function appendToMessage(msgId: string, text: unknown) {
    const msg = messages.value.find(m => m.id === msgId)
    if (!msg) return
    
    const safe = typeof text === 'string' ? text : JSON.stringify(text ?? '')
    msg.content += safe
  }

  // 完成消息
  function finalizeMessage(msgId: string) {
    const msg = messages.value.find(m => m.id === msgId)
    if (!msg) return
    
    msg.streaming = false
    if (!msg.content) {
      msg.content = '✅ 行程方案已生成！你可以在右侧画布中查看和编辑。'
    }
    scrollToBottom()
  }

  // 滚动到底部
  async function scrollToBottom() {
    await nextTick()
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  }

  // 格式化内容（增强Markdown）
  function formatContent(content: unknown): string {
    const text = typeof content === 'string' ? content : JSON.stringify(content ?? '')
    if (!text) return ''
    
    let html = text
      // 代码块（```...```）
      .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre class="md-code-block"><code>$2</code></pre>')
      // 行内代码（`...`）
      .replace(/`(.*?)`/g, '<code class="md-inline-code">$1</code>')
      // 标题（###、##、#）
      .replace(/^### (.*$)/gm, '<h4 class="md-h3">$1</h4>')
      .replace(/^## (.*$)/gm, '<h3 class="md-h2">$1</h3>')
      .replace(/^# (.*$)/gm, '<h2 class="md-h1">$1</h2>')
      // 粗体（**...**）
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      // 斜体（*...*）
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      // 无序列表
      .replace(/^[\s]*[-*+] (.*$)/gm, '<li class="md-li">$1</li>')
      // 有序列表
      .replace(/^[\s]*\d+\. (.*$)/gm, '<li class="md-oli">$1</li>')
      // 分隔线
      .replace(/^[-*_]{3,}$/gm, '<hr class="md-hr">')
      // 链接
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener" class="md-link">$1</a>')
      // 换行
      .replace(/\n/g, '<br>')
    
    // 处理列表：将连续的li包裹在ul/ol中
    html = html
      .replace(/(<li class="md-li">.*?<\/li>(?:<br>)?)+/g, (match) => {
        return '<ul class="md-ul">' + match.replace(/<br>/g, '') + '</ul>'
      })
      .replace(/(<li class="md-oli">.*?<\/li>(?:<br>)?)+/g, (match) => {
        return '<ol class="md-ol">' + match.replace(/<br>/g, '') + '</ol>'
      })
    
    return html
  }

  // 格式化时间
  function formatTime(d: Date): string {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  return {
    messages,
    messagesRef,
    addUserMessage,
    addAssistantMessage,
    updateMessage,
    appendToMessage,
    finalizeMessage,
    scrollToBottom,
    formatContent,
    formatTime,
  }
}