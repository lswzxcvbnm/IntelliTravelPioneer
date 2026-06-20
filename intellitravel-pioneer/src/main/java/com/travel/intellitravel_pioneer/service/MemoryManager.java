package com.travel.intellitravel_pioneer.service;
import com.travel.intellitravel_pioneer.po.UserLongTermMemoryPO;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.math.BigDecimal;
import com.travel.intellitravel_pioneer.repository.UserLongTermMemoryRepository;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.travel.intellitravel_pioneer.po.ItineraryPO;
import com.travel.intellitravel_pioneer.po.UserHealthPO;
import com.travel.intellitravel_pioneer.repository.ItineraryRepository;
import com.travel.intellitravel_pioneer.repository.UserHealthRepository;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.vo.conversation.AgentMessageVO;
import com.travel.intellitravel_pioneer.vo.itinerary.DayPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.RouteItem;

import lombok.Data;

@Component
public class MemoryManager {
    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private UserLongTermMemoryRepository longTermMemoryRepository;
    @Autowired
    private UserGlobalPreferenceService globalPreferenceService;
    // ==================== 短期记忆（当前对话） ====================
    public List<AgentMessageVO> getShortTermMemory(String conversationId) {
        List<AgentMessageVO> allMessages = conversationService.getConversationMessages(null, conversationId);
        int maxSize = 10;
        if (allMessages == null || allMessages.isEmpty()) {
            return List.of();
        }
        if (allMessages.size() <= maxSize) {
            return allMessages;
        }
        return allMessages.subList(allMessages.size() - maxSize, allMessages.size());
    }

    // ==================== 工作记忆（当前任务状态） ====================

    @Data
    public static class WorkingMemory {
        private String currentIntent;
        private ItineraryPlan draftPlan;
        private Map<String, Object> entities = new HashMap<>();
        private List<String> pendingQueries;
        private List<String> completedSteps;
        private LocalDateTime lastUpdate;
    }

    private final Map<String, WorkingMemory> workingMemoryMap = new ConcurrentHashMap<>();

    public WorkingMemory getWorkingMemory(String conversationId) {
        WorkingMemory memory = workingMemoryMap.computeIfAbsent(conversationId, k -> {
            log.info("【工作记忆】创建新的工作记忆 for conversation: {}", conversationId);
            return new WorkingMemory();
        });
        log.debug("【工作记忆】当前 draftPlan: {}", memory.getDraftPlan() != null ? "存在" : "为空");
        return memory;
    }

    public void updateWorkingMemory(String conversationId, Consumer<WorkingMemory> updater) {
        log.info("【工作记忆】更新工作记忆 for conversation: {}", conversationId);
        WorkingMemory memory = getWorkingMemory(conversationId);
        updater.accept(memory);
        memory.setLastUpdate(LocalDateTime.now());
        workingMemoryMap.put(conversationId, memory);
        log.info("【工作记忆】更新完成 - currentIntent: {}, 有draftPlan: {}",
                memory.getCurrentIntent(), memory.getDraftPlan() != null);
    }

    public void clearWorkingMemory(String conversationId) {
        workingMemoryMap.remove(conversationId);
    }

    // ==================== 长期记忆（用户偏好） ====================

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserHealthRepository userHealthRepository;

    @Autowired
    private ItineraryRepository itineraryRepository;


     //构建用户画像 Prompt
    public String buildUserProfileContext(Integer userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 用户画像（长期记忆）\n");
        UserHealthPO health = userHealthRepository.findByUserId(userId);
        if (health != null) {
            sb.append("- 体能等级：").append(estimateFitnessLevel(health)).append("\n");
            sb.append("- 运动习惯：").append(health.getExerciseDaysPerWeek()).append("天/周\n");
            if (health.getCombatPower() != null) {
                sb.append("- 战力值：").append(health.getCombatPower()).append("\n");
            }
        }
        List<ItineraryPO> histories = itineraryRepository.findByUserId(String.valueOf(userId));
        if (histories != null && !histories.isEmpty()) {
            Map<String, Integer> destFrequency = new HashMap<>();
            Map<String, Integer> typePreference = new HashMap<>();

            for (ItineraryPO history : histories) {
                ItineraryPlan plan = history.getPlan();
                if (plan != null) {
                    if (plan.getDestination() != null && !plan.getDestination().isBlank()) {
                        destFrequency.merge(plan.getDestination(), 1, Integer::sum);
                    }
                    if (plan.getDailyPlans() != null) {
                        for (DayPlan day : plan.getDailyPlans()) {
                            if (day.getItems() != null) {
                                for (RouteItem item : day.getItems()) {
                                    typePreference.merge(item.getType(), 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }

            if (!destFrequency.isEmpty()) {
                String topDest = destFrequency.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("");
                sb.append("- 常去目的地：").append(topDest).append("\n");
            }

            if (!typePreference.isEmpty()) {
                String topType = typePreference.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("");
                sb.append("- 偏好类型：").append(topType).append("\n");
            }
        }

        // 添加用户全局偏好（跨对话知识共享）
        try {
            String globalPreferences = globalPreferenceService.buildGlobalPreferenceContext(String.valueOf(userId));
            if (globalPreferences != null && !globalPreferences.isBlank()) {
                sb.append(globalPreferences);
            }
        } catch (Exception e) {
            log.warn("【长期记忆】获取用户全局偏好失败: {}", e.getMessage());
        }

        return sb.toString();
    }

    private String estimateFitnessLevel(UserHealthPO health) {
        if (health.getExerciseDaysPerWeek() == null) return "中等";
        int days = health.getExerciseDaysPerWeek();
        if (days >= 5) return "高";
        if (days >= 2) return "中等";
        return "低";
    }


    public void saveUserPreference(String userId, String memoryType, String key, String value) {
        log.info("【长期记忆】保存用户偏好 - userId: {}, type: {}, key: {}, value: {}",
                userId, memoryType, key, value);
        UserLongTermMemoryPO memory = new UserLongTermMemoryPO();
        memory.setUserId(userId);
        memory.setMemoryType(memoryType);
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        memory.setConfidence(BigDecimal.valueOf(0.8));
        memory.setCreatedAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());
        longTermMemoryRepository.save(memory);
    }

    // 在行程生成完成后，提取偏好并保存
    public void extractAndSavePreferences(String userId, ItineraryRequest request, ItineraryPlan plan) {
        log.info("【长期记忆】开始提取并保存用户偏好 - userId: {}", userId);
        if (request.getDestination() != null) {
            saveUserPreference(userId, "DESTINATION", request.getDestination(), "1");
        }
        if (request.getBudgetLevel() != null) {
            saveUserPreference(userId, "BUDGET", request.getBudgetLevel(), "1");
        }
        if (request.getInterests() != null) {
            for (String interest : request.getInterests()) {
                saveUserPreference(userId, "INTEREST", interest, "1");
            }
        }
    }
}