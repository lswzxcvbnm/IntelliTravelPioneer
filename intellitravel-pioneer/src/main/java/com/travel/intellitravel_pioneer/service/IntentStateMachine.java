package com.travel.intellitravel_pioneer.service;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component
public class IntentStateMachine {
    private static final Logger log = LoggerFactory.getLogger(IntentStateMachine.class);
    public enum Intent {
        PLAN_START,
        PLAN_MODIFY,
        PLAN_QUERY,
        PLAN_CONFIRM,
        PLAN_COMPLETE,
        PLAN_REPEAT_LAST,
        PLAN_REPEAT_PREVIOUS,       // 重复上上次
        PLAN_REPEAT_BY_NUMBER,      //  按序号重复
        PLAN_SHOW_HISTORY,
    }

    public enum State {
        IDLE,
        GATHERING_INFO,
        PLANNING,
        MODIFYING,
        REVIEWING
    }

    private final Map<String, State> conversationState = new ConcurrentHashMap<>();
    private final Map<String, Intent> lastIntent = new ConcurrentHashMap<>();

    public State getState(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return State.IDLE;
        }
        return conversationState.getOrDefault(conversationId, State.IDLE);
    }

    public void updateState(String conversationId, State newState) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        State oldState = getState(conversationId);
        log.info("【状态维护】对话 {} 状态变更: {} -> {}", conversationId, oldState, newState);
        conversationState.put(conversationId, newState);
    }

    public Intent getLastIntent(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return lastIntent.get(conversationId);
    }

    public void updateIntent(String conversationId, Intent intent) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        lastIntent.put(conversationId, intent);
    }

    public Intent detectIntent(String userMessage) {
        if (userMessage == null) return Intent.PLAN_START;

        // 显示操作历史
        if (userMessage.contains("操作历史") || userMessage.contains("历史记录") ||
                userMessage.contains("我做过什么") || userMessage.contains("查看历史")) {
            return Intent.PLAN_SHOW_HISTORY;
        }

        // 按序号重复：重复第2个请求、重复第一个命令、重复第3次操作
        Pattern numberPattern = Pattern.compile("重复第(\\d+)(?:个|次|条)?(?:请求|命令|操作)?");
        Matcher numberMatcher = numberPattern.matcher(userMessage);
        if (numberMatcher.find()) {
            return Intent.PLAN_REPEAT_BY_NUMBER;
        }


        // 重复上一个命令的上一个命令
        if (userMessage.contains("上一个命令的上一个命令") ||
                userMessage.contains("上上一个") ||
                userMessage.contains("上上个命令")) {
            return Intent.PLAN_REPEAT_PREVIOUS;
        }

        // 重复上一个/上次
        if (userMessage.contains("重复上一个") ||
                userMessage.contains("重复上次") ||
                userMessage.contains("再来一次") ||
                userMessage.matches(".*重复.*上次.*")) {
            return Intent.PLAN_REPEAT_LAST;
        }

        if (containsAny(userMessage, "重复", "再做一次", "再来一次", "同样操作", "再执行一次", "再重复一次")) {
            log.info("【意图识别】检测到重复操作意图: {}", userMessage);
            return Intent.PLAN_REPEAT_LAST;
        }
        if (containsAny(userMessage, "规划", "生成", "安排", "帮我")) {
            log.info("【意图识别】检测到规划意图: {}", userMessage);
            return Intent.PLAN_START;
        }
        if (containsAny(userMessage, "改成", "换成", "删除", "去掉", "增加", "添加", "少", "多")) {
            log.info("【意图识别】检测到修改意图: {}", userMessage);
            return Intent.PLAN_MODIFY;
        }
        if (containsAny(userMessage, "多少", "几个", "什么", "哪个", "多少天", "几个景点")) {
            log.info("【意图识别】检测到查询意图: {}", userMessage);
            return Intent.PLAN_QUERY;
        }
        if (containsAny(userMessage, "确认", "是的", "对", "好")) {
            log.info("【意图识别】默认意图: {}", userMessage);
            return Intent.PLAN_CONFIRM;
        }
        if (containsAny(userMessage, "完成", "好了", "结束", "保存")) {
            log.info("【意图识别】默认意图: {}", userMessage);
            return Intent.PLAN_COMPLETE;
        }
        return Intent.PLAN_START;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    public String buildStatePrompt(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "";
        }
        State state = getState(conversationId);
        Intent intent = getLastIntent(conversationId);
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 对话状态\n");
        sb.append("- 当前状态: ").append(state).append("\n");
        if (intent != null) {
            sb.append("- 上一意图: ").append(intent).append("\n");
        }
        return sb.toString();
    }

    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        conversationState.remove(conversationId);
        lastIntent.remove(conversationId);
    }
}