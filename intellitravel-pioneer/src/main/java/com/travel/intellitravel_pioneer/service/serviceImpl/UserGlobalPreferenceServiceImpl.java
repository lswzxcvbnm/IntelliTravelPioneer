package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserGlobalPreferencePO;
import com.travel.intellitravel_pioneer.repository.UserGlobalPreferenceRepository;
import com.travel.intellitravel_pioneer.service.UserGlobalPreferenceService;
import com.travel.intellitravel_pioneer.vo.preference.UserGlobalPreferenceSaveRequest;
import com.travel.intellitravel_pioneer.vo.preference.UserGlobalPreferenceVO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserGlobalPreferenceServiceImpl implements UserGlobalPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserGlobalPreferenceServiceImpl.class);

    @Autowired
    private UserGlobalPreferenceRepository preferenceRepository;

    // 偏好类别定义
    private static final Map<String, Map<String, String>> CATEGORY_DEFINITIONS = new LinkedHashMap<>();

    static {
        // 回答风格
        Map<String, String> styleOptions = new LinkedHashMap<>();
        styleOptions.put("brief", "简略回答，突出重点");
        styleOptions.put("detailed", "详细回答，包含完整信息");
        styleOptions.put("professional", "专业术语，深度分析");
        styleOptions.put("casual", "轻松口语化，易于理解");
        CATEGORY_DEFINITIONS.put("STYLE", styleOptions);

        // 饮食偏好
        Map<String, String> dietOptions = new LinkedHashMap<>();
        dietOptions.put("vegetarian", "素食主义");
        dietOptions.put("vegan", "纯素食");
        dietOptions.put("halal", "清真饮食");
        dietOptions.put("no_spicy", "不吃辣");
        dietOptions.put("gluten_free", "无麸质");
        dietOptions.put("seafood_allergy", "海鲜过敏");
        CATEGORY_DEFINITIONS.put("DIET", dietOptions);

        // 体能水平
        Map<String, String> fitnessOptions = new LinkedHashMap<>();
        fitnessOptions.put("relaxed", "轻松休闲，不赶行程");
        fitnessOptions.put("moderate", "适中节奏，张弛有度");
        fitnessOptions.put("active", "积极活跃，充实安排");
        fitnessOptions.put("challenging", "挑战极限，高强度");
        CATEGORY_DEFINITIONS.put("FITNESS", fitnessOptions);

        // 特殊需求
        Map<String, String> specialOptions = new LinkedHashMap<>();
        specialOptions.put("elderly", "携带老人，需要休息");
        specialOptions.put("children", "携带儿童，适合亲子");
        specialOptions.put("wheelchair", "轮椅无障碍需求");
        specialOptions.put("pregnant", "孕妇，避免剧烈活动");
        specialOptions.put("pet", "携带宠物");
        CATEGORY_DEFINITIONS.put("SPECIAL", specialOptions);
    }

    @Override
    public List<UserGlobalPreferenceVO> getUserPreferences(String userId) {
        return preferenceRepository.findByUserIdAndEnabledTrue(userId)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserGlobalPreferenceVO> getUserPreferencesByCategory(String userId, String category) {
        return preferenceRepository.findByUserIdAndCategoryAndEnabledTrue(userId, category)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserGlobalPreferenceVO saveOrUpdatePreference(String userId, UserGlobalPreferenceSaveRequest request) {
        validateRequest(request);

        Optional<UserGlobalPreferencePO> existing = preferenceRepository
                .findByUserIdAndCategoryAndPreferenceKey(userId, request.getCategory(), request.getPreferenceKey());

        UserGlobalPreferencePO po;
        if (existing.isPresent()) {
            // 更新
            po = existing.get();
            po.setPreferenceValue(request.getPreferenceValue());
            if (request.getEnabled() != null) {
                po.setEnabled(request.getEnabled());
            }
            log.info("【全局偏好】更新用户偏好 - userId: {}, category: {}, key: {}", 
                    userId, request.getCategory(), request.getPreferenceKey());
        } else {
            // 新建
            po = UserGlobalPreferencePO.builder()
                    .userId(userId)
                    .category(request.getCategory())
                    .preferenceKey(request.getPreferenceKey())
                    .preferenceValue(request.getPreferenceValue())
                    .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                    .build();
            log.info("【全局偏好】新建用户偏好 - userId: {}, category: {}, key: {}", 
                    userId, request.getCategory(), request.getPreferenceKey());
        }

        po = preferenceRepository.save(po);
        return toVO(po);
    }

    @Override
    @Transactional
    public List<UserGlobalPreferenceVO> batchSavePreferences(String userId, List<UserGlobalPreferenceSaveRequest> requests) {
        List<UserGlobalPreferenceVO> results = new ArrayList<>();
        for (UserGlobalPreferenceSaveRequest request : requests) {
            results.add(saveOrUpdatePreference(userId, request));
        }
        return results;
    }

    @Override
    @Transactional
    public void deletePreference(String userId, String category, String preferenceKey) {
        Optional<UserGlobalPreferencePO> existing = preferenceRepository
                .findByUserIdAndCategoryAndPreferenceKey(userId, category, preferenceKey);
        
        if (existing.isEmpty()) {
            throw new IntellitravelException("偏好不存在");
        }
        
        preferenceRepository.deleteByUserIdAndCategoryAndPreferenceKey(userId, category, preferenceKey);
        log.info("【全局偏好】删除用户偏好 - userId: {}, category: {}, key: {}", userId, category, preferenceKey);
    }

    @Override
    @Transactional
    public UserGlobalPreferenceVO togglePreference(String userId, String category, String preferenceKey, boolean enabled) {
        Optional<UserGlobalPreferencePO> existing = preferenceRepository
                .findByUserIdAndCategoryAndPreferenceKey(userId, category, preferenceKey);
        
        if (existing.isEmpty()) {
            throw new IntellitravelException("偏好不存在");
        }
        
        UserGlobalPreferencePO po = existing.get();
        po.setEnabled(enabled);
        po = preferenceRepository.save(po);
        
        log.info("【全局偏好】切换偏好状态 - userId: {}, category: {}, key: {}, enabled: {}", 
                userId, category, preferenceKey, enabled);
        return toVO(po);
    }

    @Override
    public String buildGlobalPreferenceContext(String userId) {
        log.info("【全局偏好】buildGlobalPreferenceContext - userId: {}", userId);
        List<UserGlobalPreferencePO> preferences = preferenceRepository.findByUserIdAndEnabledTrue(userId);
        log.info("【全局偏好】查询到 {} 条启用的偏好", preferences != null ? preferences.size() : 0);
        
        if (preferences == null || preferences.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【用户全局偏好 - 必须严格遵守】\n");
        sb.append("以下是用户主动设置的个性化偏好，在回答时必须严格遵守：\n");

        // 按类别分组
        Map<String, List<UserGlobalPreferencePO>> grouped = preferences.stream()
                .collect(Collectors.groupingBy(UserGlobalPreferencePO::getCategory));

        for (Map.Entry<String, List<UserGlobalPreferencePO>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<UserGlobalPreferencePO> prefs = entry.getValue();
            
            sb.append("\n### ").append(getCategoryDisplayName(category)).append("\n");
            for (UserGlobalPreferencePO pref : prefs) {
                // 运行时过滤：对每条偏好内容进行安全净化
                String sanitized = sanitizePreferenceValue(pref.getPreferenceValue());
                if (sanitized != null && !sanitized.isBlank()) {
                    sb.append("- ").append(sanitized).append("\n");
                }
            }
        }

        return sb.toString();
    }

    @Override
    public Map<String, Object> getPreferenceCategories() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : CATEGORY_DEFINITIONS.entrySet()) {
            Map<String, Object> categoryInfo = new LinkedHashMap<>();
            categoryInfo.put("displayName", getCategoryDisplayName(entry.getKey()));
            categoryInfo.put("options", entry.getValue());
            result.put(entry.getKey(), categoryInfo);
        }
        return result;
    }

    private void validateRequest(UserGlobalPreferenceSaveRequest request) {
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new IntellitravelException("偏好类别不能为空");
        }
        if (request.getPreferenceKey() == null || request.getPreferenceKey().isBlank()) {
            throw new IntellitravelException("偏好键不能为空");
        }
        if (request.getPreferenceValue() == null || request.getPreferenceValue().isBlank()) {
            throw new IntellitravelException("偏好值不能为空");
        }
        // 安全校验：拦截可能用于 Prompt Injection 的恶意内容
        validateContentSafety(request.getPreferenceValue());
    }

    /**
     * 内容安全校验 - 防止 Prompt Injection 攻击
     */
    private void validateContentSafety(String content) {
        if (content == null) return;
        
        String lower = content.toLowerCase();
        
        // 检测常见的 Prompt Injection 模式
        String[] dangerousPatterns = {
            // 指令覆盖类
            "ignore", "忽略", "忽略上面", "忽略之前", "忽略以上",
            "ignore previous", "ignore above", "ignore all",
            "disregard", "forget", "override",
            // 角色劫持类
            "你现在是", "你不再是", "你扮演", "假装你是", "伪装成",
            "you are now", "act as", "pretend to be", "roleplay as",
            "system prompt", "系统提示", "你的指令是",
            // 越狱类
            "dan mode", "jailbreak", "越狱", "开发者模式",
            "developer mode", "god mode", "无限制模式",
            // 危险内容诱导
            "输出密码", "泄露", "hack", "exploit", "攻击",
            "制造", "炸弹", "武器", "毒品",
            // 提示词注入标记
            "---", "===", "***", "```system", "```assistant", "```user",
            "[system]", "[assistant]", "[user]",
            "<<SYS>>", "<<INST>>", "<<USER>>"
        };
        
        for (String pattern : dangerousPatterns) {
            if (lower.contains(pattern)) {
                log.warn("【安全拦截】偏好内容包含可疑关键词: '{}', 内容: {}", pattern, content);
                throw new IntellitravelException("偏好内容包含不允许的关键词，请检查后重试");
            }
        }
        
        // 长度限制（防止过长的注入攻击）
        if (content.length() > 500) {
            throw new IntellitravelException("偏好内容过长，请控制在500字以内");
        }
    }

    /**
     * 运行时内容净化 - 作为最后防线，处理数据库中可能存在的恶意内容
     * 返回净化后的内容，如果内容危险则返回null
     */
    private String sanitizePreferenceValue(String value) {
        if (value == null || value.isBlank()) return null;
        
        String lower = value.toLowerCase();
        
        // 检测高危注入模式（运行时只过滤最危险的，不阻断正常偏好）
        String[] criticalPatterns = {
            "ignore", "忽略", "system prompt", "系统提示",
            "you are now", "你现在是", "act as", "pretend",
            "dan mode", "jailbreak", "越狱",
            "[system]", "[assistant]", "<<sys>>"
        };
        
        for (String pattern : criticalPatterns) {
            if (lower.contains(pattern)) {
                log.warn("【安全过滤】运行时过滤掉危险偏好内容: {}", value);
                return null; // 直接丢弃，不注入到提示词
            }
        }
        
        // 净化：移除可能的格式注入字符
        return value
                .replaceAll("[\\x00-\\x1F]", "") // 移除控制字符
                .replaceAll("```[a-zA-Z]*", "") // 移除代码块标记
                .trim();
    }

    private String getCategoryDisplayName(String category) {
        return switch (category) {
            case "STYLE" -> "回答风格";
            case "DIET" -> "饮食偏好";
            case "FITNESS" -> "体能水平";
            case "SPECIAL" -> "特殊需求";
            case "CUSTOM" -> "自定义规则";
            default -> category;
        };
    }

    private UserGlobalPreferenceVO toVO(UserGlobalPreferencePO po) {
        return UserGlobalPreferenceVO.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .category(po.getCategory())
                .preferenceKey(po.getPreferenceKey())
                .preferenceValue(po.getPreferenceValue())
                .enabled(po.getEnabled())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}