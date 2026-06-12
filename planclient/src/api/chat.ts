import { API_BASE_URL, http } from '../config/api'

export interface ChatMessage {
    role: 'user' | 'assistant'
    content: string
}

export interface ChatCallbacks {
    onToken?: (token: string) => void
    onDone?: (data: { conversationId: string; messageId: string }) => void
    onError?: (error: string) => void
}

/**
 * Stream AI chat conversation via SSE.
 * @param message The user message
 * @param conversationId Optional existing conversation ID for history
 * @param callbacks Stream event callbacks
 */
export async function streamChat(
    message: string,
    conversationId: string | null,
    callbacks: ChatCallbacks
): Promise<void> {
    const token = localStorage.getItem('token') || ''
    const url = `${API_BASE_URL}/api/ai/chat/stream`

    const resp = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ message, conversationId })
    })

    if (!resp.ok) {
        const errText = await resp.text().catch(() => 'Unknown error')
        callbacks.onError?.(errText)
        return
    }

    const reader = resp.body?.getReader()
    if (!reader) {
        callbacks.onError?.('无法读取响应流')
        return
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let eventName = ''

    while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
            if (line.startsWith('event: ')) {
                eventName = line.substring(7).trim()
            } else if (line.startsWith('data: ')) {
                const data = line.substring(6)
                if (eventName === 'token') {
                    callbacks.onToken?.(data)
                } else if (eventName === 'done') {
                    try {
                        const parsed = JSON.parse(data)
                        callbacks.onDone?.(parsed)
                    } catch {
                        callbacks.onDone?.({ conversationId: '', messageId: '' })
                    }
                } else if (eventName === 'error') {
                    callbacks.onError?.(data)
                }
                eventName = ''
            }
        }
    }
}
