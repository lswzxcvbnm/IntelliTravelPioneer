package com.travel.intellitravel_pioneer.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

import com.travel.intellitravel_pioneer.service.MemoryManager.WorkingMemory;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class EntityTracker {
    private static final Logger log = LoggerFactory.getLogger(ConversationContextManager.class);
    private final Map<String, ExtractedEntities> entityCache = new ConcurrentHashMap<>();
    private static final Set<String> CITY_SET = Set.of(
            "北京", "上海", "广州", "深圳", "杭州", "成都", "西安",
            "重庆", "武汉", "长沙", "南京", "苏州", "天津", "青岛",
            "厦门", "三亚", "丽江", "大理", "桂林", "黄山"
    );

    @Data
    public static class ExtractedEntities {
        private String destination;
        private Integer days;
        private String budgetLevel;
        private List<String> interests;
        private LocalDate startDate;
        private Integer groupSize;
        private String accommodationPreference;
        private List<String> excludedPlaces;
        private Set<String> mentionedPlaces = new HashSet<>();
        private Map<String, Object> temporalInfo = new HashMap<>();
    }


     //从消息中提取实体
    public ExtractedEntities extractEntities(String message, ExtractedEntities existing) {
        log.info("【实体追踪】处理消息: {}", message);
        ExtractedEntities entities = existing != null ? existing : new ExtractedEntities();

        String dest = extractDestination(message);
        if (dest != null) {
            log.info("【实体追踪】提取到目的地: {}", dest);
            entities.setDestination(dest);
        }

        Integer days = extractDays(message);
        if (days != null) {
            log.info("【实体追踪】提取到天数: {}", days);
            entities.setDays(days);
        }

        String budget = extractBudget(message);
        if (budget != null) {
            log.info("【实体追踪】提取到预算: {}", budget);
            entities.setBudgetLevel(budget);
        }

        List<String> interests = extractInterests(message);
        if (!interests.isEmpty()) {
            log.info("【实体追踪】提取到兴趣: {}", interests);
            if (entities.getInterests() == null) {
                entities.setInterests(interests);
            } else {
                for (String interest : interests) {
                    if (!entities.getInterests().contains(interest)) {
                        entities.getInterests().add(interest);
                    }
                }
            }
        }

        Set<String> places = extractPlaces(message);
        if (!places.isEmpty()) {
            log.info("【实体追踪】提取到地点: {}", places);
        }
        if (entities.getMentionedPlaces() == null) {

            entities.setMentionedPlaces(new HashSet<>());
        }
        entities.getMentionedPlaces().addAll(places);
        log.info("【实体追踪】当前累计实体: 目的地={}, 天数={}, 预算={}, 兴趣={}",
                entities.getDestination(), entities.getDays(),
                entities.getBudgetLevel(), entities.getInterests());
        return entities;
    }


     //检测矛盾
    public Optional<Contradiction> detectContradiction(
            String newMessage,
            ExtractedEntities currentEntities,
            MemoryManager.WorkingMemory workingMemory) {

        List<String> conflicts = new ArrayList<>();

        Integer newDays = extractDays(newMessage);
        if (newDays != null && currentEntities.getDays() != null
                && !newDays.equals(currentEntities.getDays())) {
            conflicts.add(String.format("天数矛盾：之前说要玩%d天，现在又说%d天",
                    currentEntities.getDays(), newDays));
            log.warn("【矛盾检测】{}", conflicts.get(0));
        }

        String newDest = extractDestination(newMessage);
        if (newDest != null && currentEntities.getDestination() != null
                && !newDest.equals(currentEntities.getDestination())) {
            conflicts.add(String.format("目的地矛盾：之前说去%s，现在又说去%s",
                    currentEntities.getDestination(), newDest));
            log.warn("【矛盾检测】{}", conflicts.get(0));
        }

        String newBudget = extractBudget(newMessage);
        if (newBudget != null && currentEntities.getBudgetLevel() != null
                && !newBudget.equals(currentEntities.getBudgetLevel())) {
            conflicts.add(String.format("预算矛盾：之前选择%s预算，现在改为%s",
                    currentEntities.getBudgetLevel(), newBudget));
            log.warn("【矛盾检测】{}", conflicts.get(0));
        }

        if (!conflicts.isEmpty()) {
            log.warn("【矛盾检测】发现 {} 个矛盾", conflicts.size());
            return Optional.of(new Contradiction(conflicts, newMessage));
        }

        log.info("【矛盾检测】未发现矛盾");
        return Optional.empty();
    }

    private String extractDestination(String text) {
        String cityPattern = String.join("|", CITY_SET);
        Pattern pattern = Pattern.compile("(" + cityPattern + ")|去([\\u4e00-\\u9fa5]{2,5})[游玩]?");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        return null;
    }

    private Integer extractDays(String text) {
        Pattern pattern = Pattern.compile("(\\d+)[天日]");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private String extractBudget(String text) {
        if (text.contains("经济") || text.contains("低预算")) return "low";
        if (text.contains("舒适") || text.contains("中等")) return "medium";
        if (text.contains("豪华") || text.contains("高预算")) return "high";
        return null;
    }

    private List<String> extractInterests(String text) {
        List<String> interests = new ArrayList<>();
        Map<String, String> interestMap = Map.ofEntries(
                Map.entry("历史", "history"),
                Map.entry("文化", "history"),
                Map.entry("博物馆", "history"),
                Map.entry("美食", "food"),
                Map.entry("小吃", "food"),
                Map.entry("自然", "nature"),
                Map.entry("徒步", "nature"),
                Map.entry("爬山", "nature"),
                Map.entry("城市", "citywalk"),
                Map.entry("逛街", "citywalk"),
                Map.entry("夜游", "night"),
                Map.entry("夜景", "night")
        );
        for (Map.Entry<String, String> entry : interestMap.entrySet()) {
            if (text.contains(entry.getKey())) {
                interests.add(entry.getValue());
            }
        }
        return interests;
    }

    private Set<String> extractPlaces(String text) {
        Set<String> places = new HashSet<>();
        Pattern pattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,5})(?:景点|公园|寺|庙|塔|山|湖|园|馆)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            places.add(matcher.group(1));
        }
        return places;
    }

    @Data
    @AllArgsConstructor
    public static class Contradiction {
        private List<String> conflicts;
        private String originalMessage;
    }

    public ExtractedEntities loadEntities(String conversationId) {
        return entityCache.get(conversationId);
    }


    public void saveEntities(String conversationId, ExtractedEntities entities) {
        entityCache.put(conversationId, entities);
    }
}