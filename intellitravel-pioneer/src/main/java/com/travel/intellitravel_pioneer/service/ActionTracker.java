package com.travel.intellitravel_pioneer.service;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class ActionTracker {
    private static final Logger log = LoggerFactory.getLogger(ActionTracker.class);
    private static final int MAX_HISTORY_SIZE = 50;


    public static class UserAction {
        private String id;
        private  String actionType;      // DELETE, ADD, MODIFY, UPDATE_BUDGET
        private  String description;
        private  Map<String, Object> parameters;
        private  LocalDateTime timestamp;
        private int sequenceNumber;     // 序号（从1开始）

        public UserAction() {}

        public UserAction(String id, String actionType, String description,
                          Map<String, Object> parameters, LocalDateTime timestamp, int sequenceNumber) {
            this.id = id;
            this.actionType = actionType;
            this.description = description;
            this.parameters = parameters;
            this.timestamp = timestamp;
            this.sequenceNumber = sequenceNumber;
        }


        public String getId() { return id; }
        public String getActionType() { return actionType; }
        public String getDescription() { return description; }
        public Map<String, Object> getParameters() { return parameters; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public int getSequenceNumber() { return sequenceNumber; }


        public void setId(String id) { this.id = id; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public void setDescription(String description) { this.description = description; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String actionType;
            private String description;
            private Map<String, Object> parameters;
            private LocalDateTime timestamp;
            private int sequenceNumber;

            public Builder id(String id) { this.id = id; return this; }
            public Builder actionType(String actionType) { this.actionType = actionType; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }
            public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
            public Builder sequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; return this; }

            public UserAction build() {
                return new UserAction(id, actionType, description, parameters, timestamp, sequenceNumber);
            }
        }
    }


    // 存储每个对话的操作历史（最多保留20条）
    private final Map<String, List<UserAction>> actionHistory = new ConcurrentHashMap<>();

    public List<UserAction> getActionHistory(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return Collections.emptyList();
        List<UserAction> history = actionHistory.get(conversationId);
        return history != null ? new ArrayList<>(history) : Collections.emptyList();
    }


    public void recordAction(String conversationId, String actionType, String description, Map<String, Object> params) {
        if (conversationId == null || conversationId.isBlank()) return;

        List<UserAction> history = actionHistory.computeIfAbsent(conversationId, k -> new ArrayList<>());

        int seqNum = history.size() + 1;
        UserAction action = UserAction.builder()
                .id(UUID.randomUUID().toString())
                .actionType(actionType)
                .description(description)
                .parameters(params)
                .timestamp(LocalDateTime.now())
                .sequenceNumber(seqNum)
                .build();

        history.add(action);

        // 保持历史记录不超过最大值
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
            // 重新编号
            for (int i = 0; i < history.size(); i++) {
                history.get(i).setSequenceNumber(i + 1);
            }
        }

        log.info("【操作记忆】记录操作 #{}. {} - {}", seqNum, actionType, description);
    }


    public Optional<UserAction> getLastAction(String conversationId) {
        return getActionByOffset(conversationId, 0);
    }


     //获取指定偏移量的操作（0=最近，1=上一次，2=上上次）
    public Optional<UserAction> getActionByOffset(String conversationId, int offset) {
        if (conversationId == null || conversationId.isBlank()) return Optional.empty();

        List<UserAction> history = actionHistory.get(conversationId);
        if (history == null || history.isEmpty()) return Optional.empty();

        int index = history.size() - 1 - offset;
        if (index < 0 || index >= history.size()) return Optional.empty();

        return Optional.of(history.get(index));
    }


     // 按序号获取操作（1=第一个，2=第二个）
    public Optional<UserAction> getActionBySequence(String conversationId, int sequenceNumber) {
        if (conversationId == null || conversationId.isBlank()) return Optional.empty();

        List<UserAction> history = actionHistory.get(conversationId);
        if (history == null || history.isEmpty()) return Optional.empty();

        return history.stream()
                .filter(a -> a.getSequenceNumber() == sequenceNumber)
                .findFirst();
    }

    //按索引获取操作（0=第一个，n-1=最后一个）
    public Optional<UserAction> getActionByIndex(String conversationId, int index) {
        if (conversationId == null || conversationId.isBlank()) return Optional.empty();

        List<UserAction> history = actionHistory.get(conversationId);
        if (history == null || history.isEmpty()) return Optional.empty();
        if (index < 0 || index >= history.size()) return Optional.empty();

        return Optional.of(history.get(index));
    }


      //获取所有操作历史（带序号）
    public String getActionHistorySummary(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return "";

        List<UserAction> history = actionHistory.get(conversationId);
        if (history == null || history.isEmpty()) return "暂无操作历史";

        StringBuilder sb = new StringBuilder();
        sb.append("操作历史记录：\n");
        for (UserAction action : history) {
            sb.append(String.format("  %d. [%s] %s (时间: %s)\n",
                    action.getSequenceNumber(),
                    action.getActionType(),
                    action.getDescription().length() > 50 ? action.getDescription().substring(0, 50) + "..." : action.getDescription(),
                    action.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
        }
        return sb.toString();
    }


     //清除对话的操作历史
    public void clearHistory(String conversationId) {
        actionHistory.remove(conversationId);
    }
}