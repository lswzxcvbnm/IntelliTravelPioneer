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

  // 格式化内容（简单Markdown）
  function formatContent(content: unknown): string {
    const text = typeof content === 'string' ? content : JSON.stringify(content ?? '')
    if (!text) return ''
    return text
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/`(.*?)`/g, '<code>$1</code>')
      .replace(/\n/g, '<br>')
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