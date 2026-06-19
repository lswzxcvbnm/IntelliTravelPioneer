import { axios } from '../utils/request'
import { API_MODULE } from './_prefix'

const PREFERENCE_MODULE = `${API_MODULE}/preference`

// 偏好类别定义
export interface PreferenceCategory {
    displayName: string
    options: Record<string, string>
}

// 用户全局偏好
export interface UserGlobalPreferenceVO {
    id: number
    userId: string
    category: string
    preferenceKey: string
    preferenceValue: string
    enabled: boolean
    createdAt: string
    updatedAt: string
}

// 保存偏好请求
export interface UserGlobalPreferenceSaveRequest {
    category: string
    preferenceKey: string
    preferenceValue: string
    enabled?: boolean
}

/**
 * 获取用户所有偏好
 */
export const getUserPreferences = () =>
    axios.get(PREFERENCE_MODULE).then(r => r.data)

/**
 * 获取用户指定类别的偏好
 */
export const getUserPreferencesByCategory = (category: string) =>
    axios.get(`${PREFERENCE_MODULE}/${category}`).then(r => r.data)

/**
 * 保存或更新偏好
 */
export const saveOrUpdatePreference = (data: UserGlobalPreferenceSaveRequest) =>
    axios.post(PREFERENCE_MODULE, data).then(r => r.data)

/**
 * 批量保存偏好
 */
export const batchSavePreferences = (data: UserGlobalPreferenceSaveRequest[]) =>
    axios.post(`${PREFERENCE_MODULE}/batch`, data).then(r => r.data)

/**
 * 删除偏好
 */
export const deletePreference = (category: string, preferenceKey: string) =>
    axios.delete(`${PREFERENCE_MODULE}/${category}/${preferenceKey}`).then(r => r.data)

/**
 * 切换偏好启用状态
 */
export const togglePreference = (category: string, preferenceKey: string, enabled: boolean) =>
    axios.put(`${PREFERENCE_MODULE}/${category}/${preferenceKey}/toggle`, null, {
        params: { enabled }
    }).then(r => r.data)

/**
 * 获取所有可用的偏好类别定义
 */
export const getPreferenceCategories = () =>
    axios.get(`${PREFERENCE_MODULE}/categories`).then(r => r.data)