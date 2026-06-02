export const API_MODULE = '/api'
export const USER_MODULE = `${API_MODULE}/user`
export const ITINERARY_MODULE = `${API_MODULE}/itinerary`
export const TRIPS_MODULE = `${API_MODULE}/trips`
export const TOOLS_MODULE = `${API_MODULE}/tools`

export interface ApiResponse<T> {
    success?: boolean
    code?: string | number
    msg?: string
    message?: string
    data: T
}
