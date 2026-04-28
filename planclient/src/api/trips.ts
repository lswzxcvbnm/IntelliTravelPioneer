import { axios } from '../utils/request'
import { TRIPS_MODULE } from './_prefix'

export interface ApiResponse<T> {
    success?: boolean
    code?: string | number
    msg?: string
    message?: string
    data: T
}

export interface UserRealTimeStatus {
    trip_id?: string
    timestamp: string
    heart_rate?: number
    steps?: number
    location?: {
        lat?: number
        lng?: number
        name?: string
    }
    power_cost?: number
    current_power?: number
    total_power?: number
    power_detail?: {
        walkingDistance?: number
        estimatedHeartRateAvg?: number
        estimatedHeartRateMax?: number
        fatigueIndex?: number
    }
    extInfo?: string
    trip_completed?: boolean
}

export interface UserRealTimeStatusInput extends Omit<UserRealTimeStatus, 'steps' | 'power_detail'> {
    steps?: number
    current_power?: number
    heytapHeartRateAverage?: number
    heytapHeartRateMax?: number
    power_detail?: UserRealTimeStatus['power_detail']
    heytapDailyActivity?: any | null
}

export interface CreateTripResponse {
    trip_id?: string
    message: string
}

export interface CurrentTripResponse {
    has_active_trip?: boolean
    trip_id?: string | null
    itinerary_id?: string | null
    created_at?: string
}

function toRealTimeStatus(input: UserRealTimeStatusInput): UserRealTimeStatus {
    const daily = input.heytapDailyActivity
    const avgHeartRate = Number(input.heytapHeartRateAverage ?? input.heart_rate ?? 0)
    const maxHeartRate = Number(input.heytapHeartRateMax ?? input.power_detail?.estimatedHeartRateMax ?? 0)
    return {
        trip_id: input.trip_id,
        timestamp: input.timestamp,
        heart_rate: avgHeartRate,
        steps: daily ? Number(daily.step ?? 0) : Number(input.steps ?? 0),
        location: input.location,
        power_cost: input.power_cost,
        current_power: input.current_power,
        total_power: input.total_power,
        power_detail: {
            walkingDistance: daily ? Number(daily.distance ?? 0) : Number(input.power_detail?.walkingDistance ?? 0),
            estimatedHeartRateAvg: Number(input.power_detail?.estimatedHeartRateAvg ?? avgHeartRate),
            estimatedHeartRateMax: maxHeartRate,
            fatigueIndex: Number(input.power_detail?.fatigueIndex ?? 0)
        },
        extInfo: input.extInfo,
        trip_completed: input.trip_completed
    }
}

export function createTrip(itineraryId: string, initialStatus: UserRealTimeStatusInput) {
    const payload = toRealTimeStatus(initialStatus)
    return axios.post<ApiResponse<CreateTripResponse>>(`${TRIPS_MODULE}`, payload, {
        params: {
            itinerary_id: itineraryId
        }
    })
}

export function getCurrentTrip() {
    return axios.get<ApiResponse<CurrentTripResponse>>(`${TRIPS_MODULE}/current`)
}

export function parseCurrentTrip(data?: CurrentTripResponse | null) {
    const hasActiveTrip = Boolean(data?.has_active_trip)
    const tripId = String(data?.trip_id ?? '')
    const itineraryId = String(data?.itinerary_id ?? '')
    const createdAt = data?.created_at ? new Date(data.created_at) : null
    return {
        hasActiveTrip,
        tripId,
        itineraryId,
        createdAt
    }
}

export function updateTripStatus(tripId: string, status: UserRealTimeStatusInput) {
    const payload = toRealTimeStatus(status)
    return axios.post<ApiResponse<string>>(`${TRIPS_MODULE}/${tripId}/status`, payload)
}

export function endTrip(tripId: string) {
    return axios.post<ApiResponse<string>>(`${TRIPS_MODULE}/${tripId}/end`)
}
