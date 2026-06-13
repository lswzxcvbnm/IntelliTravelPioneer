/**
 * 工具名称和图标映射常量
 * 避免在多个组件中重复定义相同的映射
 */

// 工具名称映射（中文名称）
export const TOOL_NAME_MAP: Record<string, string> = {
  maps_weather: '天气查询',
  maps_text_search: '地点搜索',
  maps_around_search: '周边搜索',
  maps_search_detail: '地点详情查询',
  maps_direction_driving: '驾车路线规划',
  maps_direction_walking: '步行路线规划',
  maps_direction_bicycling: '骑行路线规划',
  maps_direction_transit_integrated: '公共交通路线规划',
  maps_distance: '测距',
  maps_geo: '坐标解析',
  maps_regeocode: '地址解析',
  maps_ip_location: 'IP定位',
  maps_schema_personal_map: '地图展示',
  maps_schema_navi: '导航链接',
  maps_schema_take_taxi: '打车链接',
  search_destination_info: '目的地信息查询',
}

// 工具图标映射
export const TOOL_ICON_MAP: Record<string, string> = {
  maps_weather: '🌤️',
  maps_text_search: '🔍',
  maps_around_search: '📍',
  maps_search_detail: '📋',
  maps_direction_driving: '🚗',
  maps_direction_walking: '🚶',
  maps_direction_bicycling: '🚲',
  maps_direction_transit_integrated: '🚌',
  maps_distance: '📏',
  maps_geo: '🗺',
  maps_regeocode: '🏠',
  maps_ip_location: '🌐',
  maps_schema_personal_map: '🗺️',
  maps_schema_navi: '🧭',
  maps_schema_take_taxi: '🚕',
  search_destination_info: '📚',
}

// 工具日志消息映射（用于显示工具调用状态）
export const TOOL_LOG_MESSAGE_MAP: Record<string, string> = {
  maps_direction_bicycling: '正在规划骑行路线...',
  maps_direction_driving: '正在规划驾车路线...',
  maps_direction_transit_integrated: '正在规划公共交通路线...',
  maps_direction_walking: '正在规划步行路线...',
  maps_distance: '正在测量距离...',
  maps_geo: '正在解析地址坐标...',
  maps_regeocode: '正在获取地址信息...',
  maps_ip_location: '正在进行定位...',
  maps_schema_personal_map: '正在生成地图展示链接...',
  maps_around_search: '正在搜索周边信息...',
  maps_search_detail: '正在查询地点详情...',
  maps_text_search: '正在搜索相关地点...',
  maps_schema_navi: '正在生成导航链接...',
  maps_schema_take_taxi: '正在生成打车链接...',
  maps_weather: '正在查询天气情况...',
  search_destination_info: '正在查询目的地旅行信息...',
}

/**
 * 获取工具显示名称
 * @param toolName 工具原始名称
 * @returns 中文显示名称，如果没有映射则返回原始名称
 */
export function getToolDisplayName(toolName: string): string {
  return TOOL_NAME_MAP[toolName] || toolName
}

/**
 * 获取工具图标
 * @param toolName 工具原始名称
 * @returns 图标字符串，如果没有映射则返回默认图标
 */
export function getToolIcon(toolName: string): string {
  return TOOL_ICON_MAP[toolName] || '🔧'
}

/**
 * 获取工具日志消息
 * @param toolName 工具原始名称
 * @returns 日志消息，如果没有映射则返回null
 */
export function getToolLogMessage(toolName: string): string | null {
  return TOOL_LOG_MESSAGE_MAP[toolName] || null
}

// 简短的工具名称映射（用于日志显示）
export const TOOL_NAME_SHORT_MAP: Record<string, string> = {
  maps_weather: '天气查询',
  maps_text_search: '地点搜索',
  maps_around_search: '周边搜索',
  maps_search_detail: '地点详情',
  maps_direction_driving: '驾车路线',
  maps_direction_walking: '步行路线',
  maps_direction_bicycling: '骑行路线',
  maps_direction_transit_integrated: '公交路线',
  maps_distance: '距离测量',
  maps_geo: '坐标解析',
  maps_regeocode: '地址解析',
  maps_schema_personal_map: '地图展示',
  maps_schema_navi: '导航链接',
  maps_schema_take_taxi: '打车链接',
  search_destination_info: '目的地信息',
}

// 类型图标映射（用于行程项目类型）
export const TYPE_ICON_MAP: Record<string, string> = {
  '景点': '📍',
  '餐饮': '🍜',
  '交通': '🚗',
  '住宿': '🏨',
  '休闲': '🎭',
  '购物': '🛍️',
}

/**
 * 从日志消息中提取工具名称
 * @param message 日志消息
 * @returns 工具显示名称，如果没有匹配则返回截断的消息
 */
export function extractToolNameFromLog(message: string): string {
  const invokeMatch = message.match(/Invoking tool: (\w+)/)
  if (invokeMatch && invokeMatch[1]) {
    return TOOL_NAME_SHORT_MAP[invokeMatch[1]] || invokeMatch[1]
  }
  return message.substring(0, 30) + (message.length > 30 ? '...' : '')
}

/**
 * 根据日志消息内容返回工具图标
 * @param message 日志消息
 * @returns 图标字符串
 */
export function getToolIconFromLog(message: string): string {
  if (message.includes('weather')) return '🌤️'
  if (message.includes('search') || message.includes('Search')) return '🔍'
  if (message.includes('direction') || message.includes('路线')) return '🗺️'
  if (message.includes('distance')) return '📏'
  if (message.includes('schema') || message.includes('地图')) return '🗺️'
  if (message.includes('info') || message.includes('destination')) return 'ℹ️'
  return '🔧'
}

/**
 * 从日志消息中提取并格式化工具操作
 * @param msg 日志消息
 * @returns 格式化后的工具操作描述
 */
export function extractActionFromLog(msg: string): string {
  const invokeMatch = msg.match(/Invoking tool: (\w+)/)
  if (invokeMatch && invokeMatch[1]) {
    return getToolLogMessage(invokeMatch[1]) || msg
  }
  const completeMatch = msg.match(/Tool (\w+) completed/)
  if (completeMatch && completeMatch[1]) {
    const name = getToolLogMessage(completeMatch[1])
    if (name) return name.replace('正在', '已完成').replace('...', '。')
  }
  return msg.slice(0, 50) + (msg.length > 50 ? '...' : '')
}