package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.AgentConversationPO;
import com.travel.intellitravel_pioneer.po.AgentMessagePO;
import com.travel.intellitravel_pioneer.repository.AgentConversationRepository;
import com.travel.intellitravel_pioneer.repository.AgentMessageRepository;
import com.travel.intellitravel_pioneer.service.ConversationService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.conversation.*;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Autowired
    private AgentConversationRepository conversationRepository;

    @Autowired
    private AgentMessageRepository messageRepository;

    @Autowired
    private TokenUtil tokenUtil;

    @Override
    public ConversationVO createConversation(String token, ConversationCreateRequest request) {
        String userId = getUserId(token);
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle() : "新对话";
        LocalDateTime now = LocalDateTime.now();

        AgentConversationPO conversation = AgentConversationPO.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(title)
                .tags(request.getTags())
                .description(request.getDescription())
                .createTime(now)
                .updateTime(now)
                .build();

        conversationRepository.save(conversation);
        return toConversationVO(conversation);
    }

    @Override
    public List<ConversationVO> listConversations(String token, String keyword, String sortBy) {
        String userId = getUserId(token);
        List<AgentConversationPO> conversations;

        if (keyword != null && !keyword.isBlank()) {
            conversations = conversationRepository.findByUserIdAndTitleContainingOrderByUpdateTimeDesc(userId, keyword);
        } else {
            if ("create_asc".equals(sortBy)) {
                conversations = conversationRepository.findAll().stream()
                        .filter(c -> c.getUserId().equals(userId))
                        .sorted((a, b) -> a.getCreateTime().compareTo(b.getCreateTime()))
                        .collect(Collectors.toList());
            } else if ("create_desc".equals(sortBy)) {
                conversations = conversationRepository.findByUserIdOrderByCreateTimeDesc(userId);
            } else if ("update_asc".equals(sortBy)) {
                conversations = conversationRepository.findByUserIdOrderByUpdateTimeAsc(userId);
            } else {
                conversations = conversationRepository.findByUserIdOrderByUpdateTimeDesc(userId);
            }
        }

        return conversations.stream()
                .map(this::toConversationVO)
                .collect(Collectors.toList());
    }

    @Override
    public ConversationDetailVO getConversationDetail(String token, String conversationId) {
        String userId = getUserId(token);
        AgentConversationPO conversation = getAndValidateConversation(conversationId, userId);

        List<AgentMessageVO> messages = messageRepository.findByConversationIdOrderByCreateTimeAsc(conversationId)
                .stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());

        return ConversationDetailVO.builder()
                .conversation(toConversationVO(conversation))
                .messages(messages)
                .build();
    }

    @Override
    public ConversationVO updateConversation(String token, String conversationId, ConversationUpdateRequest request) {
        String userId = getUserId(token);
        AgentConversationPO conversation = getAndValidateConversation(conversationId, userId);

        if (request.getTitle() != null) {
            conversation.setTitle(request.getTitle());
        }
        if (request.getTags() != null) {
            conversation.setTags(request.getTags());
        }
        if (request.getDescription() != null) {
            conversation.setDescription(request.getDescription());
        }
        conversation.setUpdateTime(LocalDateTime.now());

        conversationRepository.save(conversation);
        return toConversationVO(conversation);
    }

    @Override
    @Transactional
    public void deleteConversation(String token, String conversationId) {
        String userId = getUserId(token);
        getAndValidateConversation(conversationId, userId);

        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    @Override
    public AgentMessageVO addMessage(String token, String conversationId, AgentMessageCreateRequest request) {
        String userId = getUserId(token);
        getAndValidateConversation(conversationId, userId);

        AgentMessagePO message = AgentMessagePO.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role(request.getRole())
                .content(request.getContent())
                .toolCalls(request.getToolCalls())
                .createTime(LocalDateTime.now())
                .build();

        messageRepository.save(message);

        AgentConversationPO conversation = conversationRepository.findById(conversationId).get();
        conversation.setUpdateTime(LocalDateTime.now());
        conversationRepository.save(conversation);

        return toMessageVO(message);
    }

    @Override
    public List<AgentMessageVO> getConversationMessages(String token, String conversationId) {
        String userId = getUserId(token);
        getAndValidateConversation(conversationId, userId);

        return messageRepository.findByConversationIdOrderByCreateTimeAsc(conversationId)
                .stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());
    }

    @Override
    public void linkItinerary(String token, String conversationId, String itineraryId) {
        String userId = getUserId(token);
        AgentConversationPO conversation = getAndValidateConversation(conversationId, userId);
        conversation.setItineraryId(itineraryId);
        conversation.setUpdateTime(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    @Override
    public ConversationVO getConversationByItineraryId(String token, String itineraryId) {
        String userId = getUserId(token);
        if (itineraryId == null || itineraryId.isBlank()) {
            throw new IntellitravelException("itineraryId is required");
        }
        AgentConversationPO conversation = conversationRepository
                .findTopByItineraryIdOrderByUpdateTimeDesc(itineraryId)
                .orElse(null);
        if (conversation == null) return null;
        if (!conversation.getUserId().equals(userId)) {
            throw new IntellitravelException("Access denied");
        }
        return toConversationVO(conversation);
    }

    private String getUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("Token is required");
        }
        return String.valueOf(tokenUtil.getUser(token).getId());
    }

    private AgentConversationPO getAndValidateConversation(String conversationId, String userId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IntellitravelException("conversationId is required");
        }
        AgentConversationPO conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IntellitravelException("Conversation not found"));
        if (!conversation.getUserId().equals(userId)) {
            throw new IntellitravelException("Access denied: conversation does not belong to user");
        }
        return conversation;
    }

    private ConversationVO toConversationVO(AgentConversationPO po) {
        List<AgentMessagePO> messages = messageRepository.findByConversationIdOrderByCreateTimeAsc(po.getId());
        return ConversationVO.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .title(po.getTitle())
                .tags(po.getTags())
                .description(po.getDescription())
                .messageCount(messages != null ? messages.size() : 0)
                .itineraryId(po.getItineraryId())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private AgentMessageVO toMessageVO(AgentMessagePO po) {
        return AgentMessageVO.builder()
                .id(po.getId())
                .conversationId(po.getConversationId())
                .role(po.getRole())
                .content(po.getContent())
                .toolCalls(po.getToolCalls())
                .createTime(po.getCreateTime())
                .build();
    }
}
