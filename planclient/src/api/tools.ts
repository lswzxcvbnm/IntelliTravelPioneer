import { axios } from '../utils/request'
import { TOOLS_MODULE } from './_prefix'

export interface ToolVO {
  id: number
  toolName: string
  displayName: string
  description: string
  category: string
  parametersJson: string
  usageExample: string
  enabled: boolean
  userEnabled: boolean
  sortOrder: number
  totalCalls: number
  successCalls: number
}

export interface ToolCallHistoryVO {
  id: number
  toolName: string
  displayName: string
  itineraryId: string
  parameters: string
  resultSummary: string
  success: boolean
  callTime: string
}

export interface ToolCallHistoryPage {
  content: ToolCallHistoryVO[]
  totalElements: number
  totalPages: number
  currentPage: number
  pageSize: number
}

export const getToolList = () => {
  return axios.get(`${TOOLS_MODULE}/list`)
}

export const getToolDetail = (toolName: string) => {
  return axios.get(`${TOOLS_MODULE}/${encodeURIComponent(toolName)}`)
}

export const toggleTool = (toolName: string) => {
  return axios.put(`${TOOLS_MODULE}/${encodeURIComponent(toolName)}/toggle`)
}

export const getToolCallHistory = (toolName?: string, page = 0, size = 20) => {
  const params: Record<string, string | number> = { page, size }
  if (toolName) {
    params.toolName = toolName
  }
  return axios.get(`${TOOLS_MODULE}/calls`, { params })
}
