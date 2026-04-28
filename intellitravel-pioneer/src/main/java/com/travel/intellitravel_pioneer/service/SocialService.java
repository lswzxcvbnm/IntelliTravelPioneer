package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.vo.social.*;
import org.springframework.data.domain.Page;

import java.util.List;

public interface SocialService {

    FollowResultVO followUser(String currentUserId, String targetUserId);

    FollowResultVO unfollowUser(String currentUserId, String targetUserId);

    List<UserRelationVO> getFollowingList(String userId, String currentUserId);

    List<UserRelationVO> getFollowerList(String userId, String currentUserId);

    List<UserRelationVO> getFriendList(String userId);

    MessageVO sendMessage(String senderId, String receiverId, String content, String shareType, String shareId);

    Page<MessageVO> getConversationMessages(String user1Id, String user2Id, int page, int size);

    List<ConversationVO> getConversations(String userId);

    void markMessagesAsRead(String userId, String friendId);

    long getUnreadCount(String userId);

    UserRelationVO getUserRelation(String currentUserId, String targetUserId);

    SocialStatsVO getSocialStats(String userId);
}
