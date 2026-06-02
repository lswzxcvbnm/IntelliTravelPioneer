import { axios } from '../utils/request'
import type { ApiResponse } from '../api/_prefix'

const CONVERSATION_MODULE = '/api/conversation'

export interface ConversationVO {
    id: string
    userId: string
    title: string
    tags: string | null
    description: string | null
    messageCount: number
    itineraryId: string | null
    createTime: string
    updateTime: string
}

export interface AgentMessageVO {
    id: string
    conversationId: string
    role: string
    content: string
    toolCalls: string | null
    createTime: string
}

export interface ConversationDetailVO {
    conversation: ConversationVO
    messages: AgentMessageVO[]
}

export function createConversation(title?: string) {
    return axios.post<ApiResponse<ConversationVO>>(`${CONVERSATION_MODULE}/create`, {
        title: title || '新对话'
    })
}

export function listConversations(keyword?: string, sortBy?: string) {
    const params: Record<string, string> = {}
    if (keyword) params.keyword = keyword
    if (sortBy) params.sortBy = sortBy
    return axios.get<ApiResponse<ConversationVO[]>>(`${CONVERSATION_MODULE}/list`, { params })
}

export function getConversationDetail(conversationId: string) {
    return axios.get<ApiResponse<ConversationDetailVO>>(`${CONVERSATION_MODULE}/detail/${conversationId}`)
}

export function getConversationMessages(conversationId: string) {
    return axios.get<ApiResponse<AgentMessageVO[]>>(`${CONVERSATION_MODULE}/messages/${conversationId}`)
}

export function addMessage(conversationId: string, role: string, content: string, toolCalls?: string) {
    return axios.post<ApiResponse<AgentMessageVO>>(`${CONVERSATION_MODULE}/message/${conversationId}`, {
        role,
        content,
        toolCalls: toolCalls || null
    })
}

export function updateConversation(conversationId: string, data: { title?: string; tags?: string; description?: string }) {
    return axios.put<ApiResponse<ConversationVO>>(`${CONVERSATION_MODULE}/${conversationId}`, data)
}

export function deleteConversation(conversationId: string) {
    return axios.delete<ApiResponse<string>>(`${CONVERSATION_MODULE}/${conversationId}`)
}

export function linkItinerary(conversationId: string, itineraryId: string) {
    return axios.put<ApiResponse<string>>(`${CONVERSATION_MODULE}/${conversationId}/link-itinerary`, null, {
        params: { itineraryId }
    })
}

export function getConversationByItineraryId(itineraryId: string) {
    return axios.get<ApiResponse<ConversationVO>>(`${CONVERSATION_MODULE}/by-itinerary/${itineraryId}`)
}
