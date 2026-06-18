import { ref, computed } from 'vue'
import type { ItineraryPlan, ItineraryRequest } from '../api/itinerary'
import { generateItineraryStream, modifyItineraryStream, saveItinerary, updateItinerary, getItineraryDetail } from '../api/itinerary'
import { linkItinerary, getConversationByItineraryId } from '../api/conversation'

export function useItinerary() {
  const editablePlan = ref<ItineraryPlan | null>(null)
  const isSaving = ref(false)
  const itineraryId = ref<string | null>(null)
  const convId = ref<string | null>(null)

  // 计算属性
  const totalItems = computed(() =>
    editablePlan.value?.dailyPlans?.reduce((sum, d) => sum + (d.items?.length || 0), 0) ?? 0
  )

  // 行程规范化
  const DEFAULT_ROUTE_TYPE = '景点'
  const routeTypeOptions = [DEFAULT_ROUTE_TYPE, '餐饮', '交通', '住宿', '休闲', '购物']
  const normalizeRouteType = (v: string) => routeTypeOptions.includes(v) ? v : DEFAULT_ROUTE_TYPE

  function normalizePlan(plan: ItineraryPlan): ItineraryPlan {
    return {
      title: plan.title || '我的行程计划',
      estimatedCost: Number(plan.estimatedCost || 0),
      totalDays: Number(plan.totalDays || plan.dailyPlans?.length || 1),
      dailyPlans: plan.dailyPlans.map((day, index) => ({
        dayNumber: day.dayNumber || index + 1,
        items: day.items.map(item => ({
          type: normalizeRouteType(item.type),
          name: item.name || '未命名路线项',
          description: item.description || '暂无描述',
          location: item.location || { lat: 0, lng: 0, name: '' },
        })),
      })),
    }
  }

  // 合并优化后的行程
  function mergeRefinedPlan(original: ItineraryPlan, refined: ItineraryPlan): ItineraryPlan {
    const aiDecidedDayDeletion = refined.totalDays < original.totalDays
    const aiDecidedDayAddition = refined.totalDays > original.totalDays

    const refinedItemNames = new Set<string>()
    for (const day of refined.dailyPlans) {
      for (const item of day.items) {
        if (item.name) refinedItemNames.add(item.name)
      }
    }

    const resultDays = original.dailyPlans.map(origDay => ({
      dayNumber: origDay.dayNumber,
      items: origDay.items.filter(origItem => {
        if (!origItem.name) return true
        return refinedItemNames.has(origItem.name)
      }),
    }))

    for (const refinedDay of refined.dailyPlans) {
      if (refinedDay.dayNumber == null || refinedDay.dayNumber <= 0) continue

      if (aiDecidedDayAddition && refinedDay.dayNumber > original.totalDays) {
        resultDays.push({
          dayNumber: refinedDay.dayNumber,
          items: refinedDay.items.map(item => ({
            type: normalizeRouteType(item.type),
            name: item.name || '未命名路线项',
            description: item.description || '暂无描述',
            location: item.location || { lat: 0, lng: 0, name: '' },
          }))
        })
        continue
      }

      const existingDay = resultDays.find(d => d.dayNumber === refinedDay.dayNumber)
      if (existingDay) {
        const existingNames = new Set(existingDay.items.map(i => i.name))
        for (const refinedItem of refinedDay.items) {
          if (refinedItem.name && !existingNames.has(refinedItem.name)) {
            existingDay.items.push({
              type: normalizeRouteType(refinedItem.type),
              name: refinedItem.name,
              description: refinedItem.description || '暂无描述',
              location: refinedItem.location || { lat: 0, lng: 0, name: '' },
            })
          }
        }
      }
    }

    let finalDays: typeof resultDays
    if (aiDecidedDayDeletion) {
      const llmDayNumbers = new Set(refined.dailyPlans.map(d => d.dayNumber))
      finalDays = resultDays.filter(d => llmDayNumbers.has(d.dayNumber))
      finalDays = finalDays.map((d, i) => ({ ...d, dayNumber: i + 1 }))
    } else if (aiDecidedDayAddition) {
      finalDays = resultDays.filter(d => d.items.length > 0).map((d, i) => ({ ...d, dayNumber: i + 1 }))
    } else {
      finalDays = resultDays.filter(d => d.items.length > 0).map((d, i) => ({ ...d, dayNumber: i + 1 }))
    }

    return {
      title: refined.title || original.title,
      estimatedCost: refined.estimatedCost || original.estimatedCost,
      totalDays: finalDays.length,
      dailyPlans: finalDays.length > 0 ? finalDays : original.dailyPlans,
    }
  }

  // 保存行程
  async function saveFromWorkspace(): Promise<boolean> {
    if (!editablePlan.value) return false
    
    isSaving.value = true
    try {
      if (itineraryId.value) {
        await updateItinerary(itineraryId.value, {
          ...editablePlan.value,
          conversationId: convId.value || undefined,
          destination: editablePlan.value.destination || '',
        })
        return true
      } else {
        const res = await saveItinerary({
          conversationId: convId.value || undefined,
          title: editablePlan.value.title,
          estimatedCost: editablePlan.value.estimatedCost,
          totalDays: editablePlan.value.totalDays,
          destination: editablePlan.value.destination || '',
          dailyPlans: editablePlan.value.dailyPlans,
        })
        itineraryId.value = res.data.data
        
        if (convId.value && itineraryId.value) {
          linkItinerary(convId.value, itineraryId.value).catch(() => {})
        }
        return true
      }
    } catch (e: any) {
      throw new Error(e?.message || '保存失败')
    } finally {
      isSaving.value = false
    }
  }

  // 加载行程详情
  async function loadDetailForEdit(id: string) {
    try {
      const r = await getItineraryDetail(id)
      if (r.data?.data) {
        editablePlan.value = normalizePlan(r.data.data.plan)
        itineraryId.value = r.data.data.id
        
        // 优先使用行程中存储的 conversationId（双向绑定）
        if (r.data.data.conversationId) {
          convId.value = r.data.data.conversationId
        } else {
          // 降级：通过反向查询获取关联的对话ID
          try {
            const convRes = await getConversationByItineraryId(id)
            if (convRes.data?.code === '200' && convRes.data?.data?.id) {
              convId.value = convRes.data.data.id
            }
          } catch { /* ignore */ }
        }
      }
    } catch { /* ignore */ }
  }

  // 添加路线项
  function addItem(dayNum: number) {
    const day = editablePlan.value?.dailyPlans.find(d => d.dayNumber === dayNum)
    if (!day) return
    day.items.push({
      type: DEFAULT_ROUTE_TYPE,
      name: '新路线项',
      description: '请补充该路线项描述',
      location: { lat: 0, lng: 0, name: '' },
    })
  }

  // 删除路线项
  function deleteItem(dayNum: number, idx: number) {
    const day = editablePlan.value?.dailyPlans.find(d => d.dayNumber === dayNum)
    if (!day) return
    day.items.splice(idx, 1)
  }

  return {
    editablePlan,
    isSaving,
    itineraryId,
    convId,
    totalItems,
    normalizePlan,
    mergeRefinedPlan,
    saveFromWorkspace,
    loadDetailForEdit,
    addItem,
    deleteItem,
  }
}