import { axios } from '../utils/request'
import { USER_MODULE } from './_prefix'

export interface ApiResponse<T> {
    success?: boolean
    code?: string | number
    msg?: string
    message?: string
    data: T
}

export interface UserVO {
    id?: number
    username: string
    email?: string
    combatPower?: number
    location?: string
    roles?: string[]
}

export interface LoginRequest {
    username: string
    password?: string
}

export function login(data: LoginRequest) {
    return axios.post<ApiResponse<string>>(`${USER_MODULE}/login`, data)
}

export function getInformation(username: string) {
    return axios.get<ApiResponse<UserVO>>(`${USER_MODULE}/information`, {
        params: { username }
    })
}

export function getHealthInformation(username: string) {
    return axios.get<ApiResponse<any>>(`${USER_MODULE}/health/${username}`)
}

export function register(data: { username: string; password: string; email?: string; name?: string }) {
    return axios.post<ApiResponse<string>>(`${USER_MODULE}`, data)
}
