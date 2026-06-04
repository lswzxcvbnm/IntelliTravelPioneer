import { ref, type Ref } from 'vue'
import type { ItineraryPlan } from '../api/itinerary'

export function useDragDrop(editablePlan: Ref<ItineraryPlan | null>) {
  const draggingItem = ref<{ dayNumber: number; index: number } | null>(null)

  function onDragStart(_e: DragEvent, dn: number, idx: number) {
    draggingItem.value = { dayNumber: dn, index: idx }
  }

  function onDrop(_e: DragEvent, tdn: number, tidx: number) {
    if (!editablePlan.value || !draggingItem.value) return
    
    const s = draggingItem.value
    const sd = editablePlan.value.dailyPlans.find(d => d.dayNumber === s.dayNumber)
    const td = editablePlan.value.dailyPlans.find(d => d.dayNumber === tdn)
    
    if (!sd || !td) { draggingItem.value = null; return }
    const m = sd.items[s.index]
    if (!m) { draggingItem.value = null; return }
    
    if (s.dayNumber === tdn) {
      // 同一天内排序
      sd.items.splice(s.index, 1)
      sd.items.splice(s.index < tidx ? tidx - 1 : tidx, 0, m)
    } else {
      // 跨天移动
      sd.items.splice(s.index, 1)
      td.items.splice(tidx, 0, m)
    }
    draggingItem.value = null
  }

  function onDayDrop(tdn: number) {
    if (!editablePlan.value || !draggingItem.value) return
    
    const s = draggingItem.value
    const sd = editablePlan.value.dailyPlans.find(d => d.dayNumber === s.dayNumber)
    const td = editablePlan.value.dailyPlans.find(d => d.dayNumber === tdn)
    
    if (!sd || !td || !sd.items[s.index]) { draggingItem.value = null; return }
    const m = sd.items[s.index]
    
    if (s.dayNumber === tdn) {
      // 同一天内移动到末尾
      if (s.index < sd.items.length - 1) { 
        sd.items.splice(s.index, 1); 
        sd.items.push(m) 
      }
    } else {
      // 跨天移动到末尾
      sd.items.splice(s.index, 1)
      td.items.push(m)
    }
    draggingItem.value = null
  }

  return {
    draggingItem,
    onDragStart,
    onDrop,
    onDayDrop,
  }
}