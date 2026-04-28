export interface ItineraryItem {
  id: number
  name: string
  lat: number
  lon: number
  type: string
  estimatedCp: number
  actualCp?: number
  status: string
  startTime: string
  endTime: string
}

export interface Trip {
  id: number
  title: string
  description: string
  status: string
  combatPowerTarget: number
  itineraryItems: ItineraryItem[]
  createdAt: string
}

export interface User {
  id?: number
  username: string
  email?: string
  combatPower?: number
  roles?: string[]
}
