import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Trip } from '../types'
import { generateItinerary as generateItineraryApi } from '../api/itinerary'

export const useItineraryStore = defineStore('itinerary', () => {
  const currentTrip = ref<Trip | null>(null)
  const loading = ref(false)
  const errorMessage = ref('')

  const generateItinerary = async (destination: string, days: number, intensity: string) => {
    loading.value = true
    errorMessage.value = ''

    try {
      const response = await generateItineraryApi({
        destination,
        days,
        interests: [],
        budgetLevel: intensity,
        additionalNotes: ''
      })

      currentTrip.value = response.data.data as unknown as Trip
    } catch (error) {
      console.error(error)
      errorMessage.value = '行程生成失败，请稍后重试'
      throw error
    } finally {
      loading.value = false
    }
  }

  return { currentTrip, loading, errorMessage, generateItinerary }
})
