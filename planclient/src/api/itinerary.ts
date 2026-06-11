import { axios } from '../utils/request'
import { ITINERARY_MODULE } from './_prefix'
import { getAuthToken } from '../utils/authToken'

const AI_GENERATE_TIMEOUT = 120000

export interface ApiResponse<T> {
    code: number
    msg: string
    data: T
}

export interface Location {
    lat: number
    lng: number
    name?: string
}

export interface RouteItem {
    type: string
    name: string
    description: string
    location: Location
}

export interface DayPlan {
    dayNumber: number
    items: RouteItem[]
}

export interface ItineraryPlan {
    title: string
    estimatedCost: number
    totalDays: number
    destination?: string
    dailyPlans: DayPlan[]
}

export interface ItineraryRequest {
    destination: string
    days: number
    interests: string[]
    budgetLevel: string
    additionalNotes: string
    skillIds?: string[]
}

export interface ItinerarySaveRequest {
    title: string
    estimatedCost: number
    totalDays: number
    destination?: string
    dailyPlans: DayPlan[]
    stats?: GenerationStats
}

export interface ItineraryVO {
    id: string
    userId: string
    title: string
    destination?: string
    plan: ItineraryPlan
    stats?: GenerationStats
    createTime: string
    updateTime: string
}

export interface PhaseData {
    type: string
    message: string
}

export interface ToolCallRecord {
    name: string
    displayName: string
    status: string
}

export interface GenerationStats {
    totalPromptTokens: number
    totalCompletionTokens: number
    totalTokens: number
    toolCalls: ToolCallRecord[]
    planCalls: number
    executeCalls: number
    verifyCalls: number
}

export async function generateItineraryStream(
    data: ItineraryRequest,
    onPhase: (phase: PhaseData) => void,
    onThought: (msg: string) => void,
    onToolCall: (msg: string) => void,
    onResult: (plan: ItineraryPlan) => void,
    onError: (err: any) => void,
    onStats?: (stats: GenerationStats) => void,
    onConversationId?: (id: string) => void
) {
    const token = getAuthToken();
    try {
        const response = await fetch(`${axios.defaults.baseURL}${ITINERARY_MODULE}/generate/stream`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'token': token
            },
            body: JSON.stringify(data)
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const reader = response.body?.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let eventName = '';
        let eventData = '';

        if (!reader) {
            throw new Error('Response stream not supported');
        }

        let receivedResult = false;

        while (true) {
            const { value, done } = await reader.read();
            if (done) {
                console.log('SSE stream ended normally');
                break;
            }

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split(/\r?\n/);
            buffer = lines.pop() || '';

            for (const line of lines) {
                if (line.startsWith('event:')) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith('data:')) {
                    const dataContent = line.substring(5).replace(/^\s/, '');
                    eventData = eventData ? eventData + '\n' + dataContent : dataContent;
                } else if (line === '') {
                    if (eventName && eventData) {
                        try {
                            if (eventName === 'phase') {
                                onPhase(JSON.parse(eventData));
                            } else if (eventName === 'thought') {
                                onThought(eventData);
                            } else if (eventName === 'tool') {
                                onToolCall(eventData);
                            } else if (eventName === 'result') {
                                receivedResult = true;
                                console.log('Received result event, data length:', eventData.length);
                                onResult(JSON.parse(eventData));
                            } else if (eventName === 'error') {
                                console.error('Received error event:', eventData);
                                onError(new Error(eventData));
                            } else if (eventName === 'stats') {
                                if (onStats) {
                                    onStats(JSON.parse(eventData));
                                }
                            } else if (eventName === 'conversationId') {
                                if (onConversationId) {
                                    onConversationId(eventData);
                                }
                            }
                        } catch (e) {
                            console.error('Failed to process event/data:', eventName, eventData, e);
                        }
                    }
                    eventName = '';
                    eventData = '';
                }
            }
        }

        if (!receivedResult) {
            console.error('Stream ended without receiving result');
            onError(new Error('AI 规划连接中断，请重试'));
        }
    } catch (err) {
        console.error('SSE stream error:', err);
        onError(err);
    }
}

export async function modifyItineraryStream(
    request: ItineraryRequest,
    currentPlan: ItineraryPlan,
    userPrompt: string,
    conversationId: string | null,
    onPhase: (phase: PhaseData) => void,
    onThought: (msg: string) => void,
    onToolCall: (msg: string) => void,
    onResult: (plan: ItineraryPlan) => void,
    onError: (err: any) => void,
    onStats?: (stats: GenerationStats) => void,
    onConversationId?: (id: string) => void
) {
    const token = getAuthToken();
    try {
        const response = await fetch(`${axios.defaults.baseURL}${ITINERARY_MODULE}/modify/stream`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'token': token
            },
            body: JSON.stringify({ request, currentPlan, userPrompt, conversationId })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const reader = response.body?.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let eventName = '';
        let eventData = '';

        if (!reader) {
            throw new Error('Response stream not supported');
        }

        let receivedResult = false;

        while (true) {
            const { value, done } = await reader.read();
            if (done) {
                console.log('SSE stream ended normally (modify)');
                break;
            }

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split(/\r?\n/);
            buffer = lines.pop() || '';

            for (const line of lines) {
                if (line.startsWith('event:')) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith('data:')) {
                    const dataContent = line.substring(5).replace(/^\s/, '');
                    eventData = eventData ? eventData + '\n' + dataContent : dataContent;
                } else if (line === '') {
                    if (eventName && eventData) {
                        try {
                            if (eventName === 'phase') {
                                onPhase(JSON.parse(eventData));
                            } else if (eventName === 'thought') {
                                onThought(eventData);
                            } else if (eventName === 'tool') {
                                onToolCall(eventData);
                            } else if (eventName === 'result') {
                                receivedResult = true;
                                console.log('Received result event (modify), data length:', eventData.length);
                                onResult(JSON.parse(eventData));
                            } else if (eventName === 'error') {
                                console.error('Received error event (modify):', eventData);
                                onError(new Error(eventData));
                            } else if (eventName === 'stats') {
                                if (onStats) {
                                    onStats(JSON.parse(eventData));
                                }
                            } else if (eventName === 'conversationId') {
                                if (onConversationId) {
                                    onConversationId(eventData);
                                }
                            }
                        } catch (e) {
                            console.error('Failed to process event/data:', eventName, eventData, e);
                        }
                    }
                    eventName = '';
                    eventData = '';
                }
            }
        }

        if (!receivedResult) {
            console.error('Stream ended without receiving result (modify)');
            onError(new Error('AI 修改连接中断，请重试'));
        }
    } catch (err) {
        console.error('SSE stream error (modify):', err);
        onError(err);
    }
}

export function generateItinerary(data: ItineraryRequest) {
    return axios.post<ApiResponse<ItineraryPlan>>(`${ITINERARY_MODULE}/generate`, data, {
        timeout: AI_GENERATE_TIMEOUT
    })
}

export function saveItinerary(data: ItinerarySaveRequest) {
    return axios.post<ApiResponse<string>>(`${ITINERARY_MODULE}/save`, data)
}

export function updateItinerary(itineraryId: string, data: ItinerarySaveRequest) {
    return axios.put<ApiResponse<string>>(`${ITINERARY_MODULE}/update`, data, {
        params: { itineraryId }
    })
}

export function getItineraryDetail(itineraryId: string) {
    return axios.get<ApiResponse<ItineraryVO>>(`${ITINERARY_MODULE}/detail/${itineraryId}`)
}

export function getAllItineraries() {
    return axios.get<ApiResponse<string[]>>(`${ITINERARY_MODULE}/getall`)
}

export function deleteItinerary(itineraryId: string) {
    return axios.delete<ApiResponse<string>>(`${ITINERARY_MODULE}/delete`, {
        params: { itineraryId }
    })
}
