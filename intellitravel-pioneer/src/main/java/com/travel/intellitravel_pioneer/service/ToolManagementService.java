package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.po.*;
import com.travel.intellitravel_pioneer.repository.*;
import com.travel.intellitravel_pioneer.vo.tools.ToolCallHistoryVO;
import com.travel.intellitravel_pioneer.vo.tools.ToolVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolManagementService {

    private final ToolDefinitionRepository toolDefinitionRepository;
    private final UserToolPreferenceRepository userToolPreferenceRepository;
    private final ToolCallHistoryRepository toolCallHistoryRepository;
    private static final Map<String, String[]> TOOL_SEED_DATA = new LinkedHashMap<>() {{
        put("maps_weather", new String[]{"天气查询", "地图", "查询指定城市的实时天气信息，包括温度、湿度、风力、天气状况等",
                "[{\"name\":\"city\",\"type\":\"string\",\"required\":true,\"description\":\"城市名称，如北京\"}]",
                "查询北京的天气"});
        put("maps_text_search", new String[]{"地点搜索", "地图", "根据关键词搜索地理位置信息，返回地点名称、地址和经纬度坐标",
                "[{\"name\":\"keywords\",\"type\":\"string\",\"required\":true,\"description\":\"搜索关键词\"},{\"name\":\"city\",\"type\":\"string\",\"required\":false,\"description\":\"限定城市\"}]",
                "搜索北京的故宫博物院"});
        put("maps_around_search", new String[]{"周边搜索", "地图", "搜索指定位置周边的POI信息，如餐饮、酒店、景点等",
                "[{\"name\":\"location\",\"type\":\"string\",\"required\":true,\"description\":\"中心点经纬度，如116.397,39.916\"},{\"name\":\"keywords\",\"type\":\"string\",\"required\":false,\"description\":\"搜索关键词\"}]",
                "搜索天安门周边1km内的餐厅"});
        put("maps_search_detail", new String[]{"地点详情查询", "地图", "查询指定POI的详细信息，包括营业时间、评分、电话等",
                "[{\"name\":\"id\",\"type\":\"string\",\"required\":true,\"description\":\"POI的唯一标识ID\"}]",
                "查询故宫博物院的详细信息"});
        put("maps_direction_driving", new String[]{"驾车路线规划", "地图", "规划驾车出行路线，返回距离、预计时间和路线步骤",
                "[{\"name\":\"origin\",\"type\":\"string\",\"required\":true,\"description\":\"起点经纬度\"},{\"name\":\"destination\",\"type\":\"string\",\"required\":true,\"description\":\"终点经纬度\"}]",
                "规划从北京到天津的驾车路线"});
        put("maps_direction_walking", new String[]{"步行路线规划", "地图", "规划步行路线，返回步行距离和预计时间",
                "[{\"name\":\"origin\",\"type\":\"string\",\"required\":true,\"description\":\"起点经纬度\"},{\"name\":\"destination\",\"type\":\"string\",\"required\":true,\"description\":\"终点经纬度\"}]",
                "规划从酒店步行到附近景点的路线"});
        put("maps_direction_bicycling", new String[]{"骑行路线规划", "地图", "规划骑行路线，返回骑行距离和预计时间",
                "[{\"name\":\"origin\",\"type\":\"string\",\"required\":true,\"description\":\"起点经纬度\"},{\"name\":\"destination\",\"type\":\"string\",\"required\":true,\"description\":\"终点经纬度\"}]",
                "规划西湖环湖骑行路线"});
        put("maps_direction_transit_integrated", new String[]{"公共交通路线规划", "地图", "规划公交/地铁出行方案，包含换乘信息和费用估算",
                "[{\"name\":\"origin\",\"type\":\"string\",\"required\":true,\"description\":\"起点经纬度\"},{\"name\":\"destination\",\"type\":\"string\",\"required\":true,\"description\":\"终点经纬度\"},{\"name\":\"city\",\"type\":\"string\",\"required\":true,\"description\":\"城市名称\"}]",
                "查询从北京南站到颐和园的公交路线"});
        put("maps_distance", new String[]{"测距", "地图", "计算两点或多点之间的直线距离",
                "[{\"name\":\"origins\",\"type\":\"string\",\"required\":true,\"description\":\"起点经纬度\"},{\"name\":\"destination\",\"type\":\"string\",\"required\":true,\"description\":\"终点经纬度\"}]",
                "计算两个景点之间的距离"});
        put("maps_geo", new String[]{"坐标解析", "地图", "将结构化地址转换为经纬度坐标（地理编码）",
                "[{\"name\":\"address\",\"type\":\"string\",\"required\":true,\"description\":\"结构化地址信息\"}]",
                "将北京市东城区景山前街4号转换为经纬度"});
        put("maps_regeocode", new String[]{"地址解析", "地图", "将经纬度坐标转换为结构化地址（逆地理编码）",
                "[{\"name\":\"location\",\"type\":\"string\",\"required\":true,\"description\":\"经纬度坐标\"}]",
                "将116.397,39.916转换为地址描述"});
        put("maps_ip_location", new String[]{"IP定位", "地图", "根据IP地址获取大致地理位置信息",
                "[{\"name\":\"ip\",\"type\":\"string\",\"required\":true,\"description\":\"IP地址\"}]",
                "根据用户IP获取当前城市"});
        put("maps_schema_personal_map", new String[]{"地图展示", "地图", "生成高德地图展示页面的链接，可在浏览器中查看",
                "[{\"name\":\"locations\",\"type\":\"array\",\"required\":true,\"description\":\"地点列表\"}]",
                "在网页地图上展示多个景点位置"});
        put("maps_schema_navi", new String[]{"导航链接", "地图", "生成高德地图导航链接，可在手机/网页上直接导航",
                "[{\"name\":\"destination\",\"type\":\"string\",\"required\":true,\"description\":\"目的地经纬度\"}]",
                "生成到故宫的导航链接"});
        put("maps_schema_take_taxi", new String[]{"打车链接", "地图", "生成高德打车链接，一键呼叫网约车",
                "[{\"name\":\"origin\",\"type\":\"string\",\"required\":true,\"description\":\"起点经纬度\"},{\"name\":\"destination\",\"type\":\"string\",\"required\":true,\"description\":\"终点经纬度\"}]",
                "一键呼叫从酒店到机场的网约车"});
        put("search_destination_info", new String[]{"目的地信息查询", "知识库", "查询目的地城市的旅行知识库，包括最佳旅行季节、热门景点、当地美食、交通建议等",
                "[{\"name\":\"destination\",\"type\":\"string\",\"required\":true,\"description\":\"目的地城市名称\"}]",
                "查询杭州的旅行攻略信息"});
    }};

    @PostConstruct
    public void seedToolDefinitions() {
        int order = 0;
        for (Map.Entry<String, String[]> entry : TOOL_SEED_DATA.entrySet()) {
            String toolName = entry.getKey();
            String[] data = entry.getValue();
            if (toolDefinitionRepository.findByToolName(toolName).isEmpty()) {
                ToolDefinitionPO def = ToolDefinitionPO.builder()
                        .toolName(toolName)
                        .displayName(data[0])
                        .category(data[1])
                        .description(data[2])
                        .parametersJson(data[3])
                        .usageExample(data[4])
                        .enabled(true)
                        .sortOrder(order)
                        .build();
                toolDefinitionRepository.save(def);
            }
            order++;
        }
    }

    public List<ToolVO> getAllTools(String userId) {
        List<ToolDefinitionPO> definitions = toolDefinitionRepository.findAllByOrderBySortOrderAsc();
        List<UserToolPreferencePO> preferences = userToolPreferenceRepository.findByUserId(userId);
        Map<String, Boolean> prefMap = preferences.stream()
                .collect(Collectors.toMap(UserToolPreferencePO::getToolName, UserToolPreferencePO::getEnabled));

        return definitions.stream().map(def -> {
            Boolean userEnabled = prefMap.getOrDefault(def.getToolName(), def.getEnabled());
            long totalCalls = toolCallHistoryRepository.countByUserIdAndToolName(userId, def.getToolName());
            long successCalls = toolCallHistoryRepository.countByUserIdAndToolNameAndSuccessTrue(userId, def.getToolName());
            return ToolVO.builder()
                    .id(def.getId())
                    .toolName(def.getToolName())
                    .displayName(def.getDisplayName())
                    .description(def.getDescription())
                    .category(def.getCategory())
                    .parametersJson(def.getParametersJson())
                    .usageExample(def.getUsageExample())
                    .enabled(def.getEnabled())
                    .userEnabled(userEnabled)
                    .sortOrder(def.getSortOrder())
                    .totalCalls(totalCalls)
                    .successCalls(successCalls)
                    .build();
        }).collect(Collectors.toList());
    }

    public ToolVO getToolDetail(String userId, String toolName) {
        ToolDefinitionPO def = toolDefinitionRepository.findByToolName(toolName)
                .orElseThrow(() -> new RuntimeException("工具不存在: " + toolName));
        UserToolPreferencePO pref = userToolPreferenceRepository
                .findByUserIdAndToolName(userId, toolName).orElse(null);
        Boolean userEnabled = pref != null ? pref.getEnabled() : def.getEnabled();
        long totalCalls = toolCallHistoryRepository.countByUserIdAndToolName(userId, toolName);
        long successCalls = toolCallHistoryRepository.countByUserIdAndToolNameAndSuccessTrue(userId, toolName);

        return ToolVO.builder()
                .id(def.getId())
                .toolName(def.getToolName())
                .displayName(def.getDisplayName())
                .description(def.getDescription())
                .category(def.getCategory())
                .parametersJson(def.getParametersJson())
                .usageExample(def.getUsageExample())
                .enabled(def.getEnabled())
                .userEnabled(userEnabled)
                .sortOrder(def.getSortOrder())
                .totalCalls(totalCalls)
                .successCalls(successCalls)
                .build();
    }

    /**
     * 检查用户是否启用了指定工具
     * 如果用户有偏好设置则使用偏好，否则使用全局默认值
     */
    public boolean isToolEnabledForUser(String userId, String toolName) {
        ToolDefinitionPO def = toolDefinitionRepository.findByToolName(toolName).orElse(null);
        if (def == null) {
            return true; // 未知工具默认放行
        }
        UserToolPreferencePO pref = userToolPreferenceRepository
                .findByUserIdAndToolName(userId, toolName)
                .orElse(null);
        return pref != null ? pref.getEnabled() : def.getEnabled();
    }

    public ToolVO toggleUserPreference(String userId, String toolName) {
        ToolDefinitionPO def = toolDefinitionRepository.findByToolName(toolName)
                .orElseThrow(() -> new RuntimeException("工具不存在: " + toolName));

        UserToolPreferencePO pref = userToolPreferenceRepository
                .findByUserIdAndToolName(userId, toolName)
                .orElse(null);

        if (pref == null) {
            pref = UserToolPreferencePO.builder()
                    .userId(userId)
                    .toolName(toolName)
                    .enabled(!def.getEnabled())
                    .build();
        } else {
            pref.setEnabled(!pref.getEnabled());
        }
        userToolPreferenceRepository.save(pref);

        return getToolDetail(userId, toolName);
    }

    public Page<ToolCallHistoryVO> getToolCallHistory(String userId, String toolName, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ToolCallHistoryPO> result;
        if (toolName != null && !toolName.isBlank()) {
            result = toolCallHistoryRepository.findByUserIdAndToolNameOrderByCallTimeDesc(userId, toolName, pageRequest);
        } else {
            result = toolCallHistoryRepository.findByUserIdOrderByCallTimeDesc(userId, pageRequest);
        }
        return result.map(this::toVO);
    }

    public void recordToolCall(String userId, String toolName, String displayName,
                                String itineraryId, String parameters, String resultSummary, Boolean success) {
        ToolCallHistoryPO history = ToolCallHistoryPO.builder()
                .userId(userId)
                .toolName(toolName)
                .displayName(displayName)
                .itineraryId(itineraryId)
                .parameters(parameters)
                .resultSummary(resultSummary)
                .success(success)
                .build();
        toolCallHistoryRepository.save(history);
    }

    private ToolCallHistoryVO toVO(ToolCallHistoryPO po) {
        return ToolCallHistoryVO.builder()
                .id(po.getId())
                .toolName(po.getToolName())
                .displayName(po.getDisplayName())
                .itineraryId(po.getItineraryId())
                .parameters(po.getParameters())
                .resultSummary(po.getResultSummary())
                .success(po.getSuccess())
                .callTime(po.getCallTime())
                .build();
    }
}
