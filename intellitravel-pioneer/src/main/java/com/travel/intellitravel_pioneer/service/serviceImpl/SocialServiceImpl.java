package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.*;
import com.travel.intellitravel_pioneer.repository.*;
import com.travel.intellitravel_pioneer.service.SocialService;
import com.travel.intellitravel_pioneer.vo.social.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SocialServiceImpl implements SocialService {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public FollowResultVO followUser(String currentUserId, String targetUserId) {
        int currentId = Integer.parseInt(currentUserId);
        int targetId = Integer.parseInt(targetUserId);

        if (currentId == targetId) {
            throw new IntellitravelException("不能关注自己");
        }

        UserPO targetUser = userRepository.findById(targetId)
                .orElseThrow(() -> new IntellitravelException("用户不存在"));

        Optional<FollowPO> existingFollow = followRepository.findByFollowerIdAndFollowingId(currentId, targetId);

        if (existingFollow.isPresent()) {
            throw new IntellitravelException("已经关注该用户");
        }

        FollowPO follow = new FollowPO();
        follow.setFollowerId(currentId);
        follow.setFollowingId(targetId);
        followRepository.save(follow);

        boolean isNowFriend = false;
        Optional<FollowPO> reverseFollow = followRepository.findByFollowerIdAndFollowingId(targetId, currentId);
        if (reverseFollow.isPresent()) {
            Optional<FriendPO> existingFriend = friendRepository.findFriendship(currentId, targetId);
            if (existingFriend.isEmpty()) {
                FriendPO friend = new FriendPO();
                friend.setUser1Id(Math.min(currentId, targetId));
                friend.setUser2Id(Math.max(currentId, targetId));
                friendRepository.save(friend);
            }
            isNowFriend = true;
        }

        FollowResultVO result = new FollowResultVO();
        result.setSuccess(true);
        result.setNowFollowing(true);
        result.setNowFriend(isNowFriend);
        result.setMessage(isNowFriend ? "关注成功，已成为好友" : "关注成功");
        return result;
    }

    @Override
    @Transactional
    public FollowResultVO unfollowUser(String currentUserId, String targetUserId) {
        int currentId = Integer.parseInt(currentUserId);
        int targetId = Integer.parseInt(targetUserId);

        Optional<FollowPO> existingFollow = followRepository.findByFollowerIdAndFollowingId(currentId, targetId);
        if (existingFollow.isEmpty()) {
            throw new IntellitravelException("未关注该用户");
        }

        followRepository.deleteByFollowerIdAndFollowingId(currentId, targetId);

        Optional<FriendPO> existingFriend = friendRepository.findFriendship(currentId, targetId);
        if (existingFriend.isPresent()) {
            friendRepository.deleteByUser1IdAndUser2Id(
                    existingFriend.get().getUser1Id(),
                    existingFriend.get().getUser2Id()
            );
        }

        FollowResultVO result = new FollowResultVO();
        result.setSuccess(true);
        result.setNowFollowing(false);
        result.setNowFriend(false);
        result.setMessage("已取消关注");
        return result;
    }

    @Override
    public List<UserRelationVO> getFollowingList(String userId, String currentUserId) {
        int uid = Integer.parseInt(userId);
        int currId = Integer.parseInt(currentUserId);

        List<FollowPO> follows = followRepository.findByFollowerIdOrderByCreatedAtDesc(uid);
        return follows.stream()
                .map(f -> convertToUserRelationVO(f.getFollowingId(), currId))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserRelationVO> getFollowerList(String userId, String currentUserId) {
        int uid = Integer.parseInt(userId);
        int currId = Integer.parseInt(currentUserId);

        List<FollowPO> followers = followRepository.findByFollowingIdOrderByCreatedAtDesc(uid);
        return followers.stream()
                .map(f -> convertToUserRelationVO(f.getFollowerId(), currId))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserRelationVO> getFriendList(String userId) {
        int uid = Integer.parseInt(userId);

        List<FriendPO> friends = friendRepository.findByUser1IdOrderByCreatedAtDesc(uid);
        friends.addAll(friendRepository.findByUser2IdOrderByCreatedAtDesc(uid));

        return friends.stream()
                .map(f -> {
                    int friendId = f.getUser1Id() == uid ? f.getUser2Id() : f.getUser1Id();
                    return convertToUserRelationVO(friendId, uid);
                })
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageVO sendMessage(String senderId, String receiverId, String content, String shareType, String shareId) {
        int sender = Integer.parseInt(senderId);
        int receiver = Integer.parseInt(receiverId);

        if (sender == receiver) {
            throw new IntellitravelException("不能给自己发送消息");
        }

        Optional<FriendPO> friendship = friendRepository.findFriendship(sender, receiver);
        if (friendship.isEmpty()) {
            throw new IntellitravelException("只能给好友发送消息");
        }

        MessagePO message = new MessagePO();
        message.setSenderId(sender);
        message.setReceiverId(receiver);
        message.setContent(content);
        message.setShareType(shareType);
        message.setShareId(shareId);
        messageRepository.save(message);

        return convertToMessageVO(message);
    }

    @Override
    public Page<MessageVO> getConversationMessages(String user1Id, String user2Id, int page, int size) {
        int u1 = Integer.parseInt(user1Id);
        int u2 = Integer.parseInt(user2Id);

        List<MessagePO> allMessages = messageRepository.findConversationMessages(u1, u2);

        List<MessageVO> messageVOs = allMessages.stream()
                .map(this::convertToMessageVO)
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(page - 1, size);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), messageVOs.size());

        List<MessageVO> pageContent = start < messageVOs.size() 
                ? messageVOs.subList(start, end) 
                : new ArrayList<>();

        return new PageImpl<>(pageContent, pageable, messageVOs.size());
    }

    @Override
    public List<ConversationVO> getConversations(String userId) {
        int uid = Integer.parseInt(userId);

        List<FriendPO> friends = friendRepository.findByUser1IdOrderByCreatedAtDesc(uid);
        friends.addAll(friendRepository.findByUser2IdOrderByCreatedAtDesc(uid));

        return friends.stream()
                .map(f -> {
                    int friendId = f.getUser1Id() == uid ? f.getUser2Id() : f.getUser1Id();

                    ConversationVO conv = new ConversationVO();
                    conv.setFriendId(friendId);

                    UserPO friend = userRepository.findById(friendId).orElse(null);
                    if (friend != null) {
                        conv.setFriendName(friend.getName());
                        conv.setFriendAvatar(friend.getAvatar());
                    }

                    Pageable pageable = PageRequest.of(0, 1);
                    Page<MessagePO> lastMsgPage = messageRepository
                            .findBySenderIdAndReceiverIdOrderByCreatedAtAsc(uid, friendId, pageable);

                    if (!lastMsgPage.isEmpty()) {
                        MessagePO lastMsg = lastMsgPage.getContent().get(0);
                        conv.setLastMessage(lastMsg.getShareType() != null ?
                                "[分享] " + lastMsg.getShareType() : lastMsg.getContent());
                        conv.setLastMessageTime(lastMsg.getCreatedAt().format(FORMATTER));
                    } else {
                        Page<MessagePO> lastMsgPage2 = messageRepository
                                .findByReceiverIdAndSenderIdOrderByCreatedAtAsc(uid, friendId, pageable);
                        if (!lastMsgPage2.isEmpty()) {
                            MessagePO lastMsg = lastMsgPage2.getContent().get(0);
                            conv.setLastMessage(lastMsg.getShareType() != null ?
                                    "[分享] " + lastMsg.getShareType() : lastMsg.getContent());
                            conv.setLastMessageTime(lastMsg.getCreatedAt().format(FORMATTER));
                        }
                    }

                    conv.setUnreadCount((int) messageRepository.countByReceiverIdAndIsReadFalse(uid));

                    return conv;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markMessagesAsRead(String userId, String friendId) {
        int uid = Integer.parseInt(userId);
        int fid = Integer.parseInt(friendId);
        messageRepository.markAsRead(uid, fid);
    }

    @Override
    public long getUnreadCount(String userId) {
        int uid = Integer.parseInt(userId);
        return messageRepository.countByReceiverIdAndIsReadFalse(uid);
    }

    @Override
    public UserRelationVO getUserRelation(String currentUserId, String targetUserId) {
        int currId = Integer.parseInt(currentUserId);
        int targetId = Integer.parseInt(targetUserId);
        return convertToUserRelationVO(targetId, currId);
    }

    private UserRelationVO convertToUserRelationVO(int targetUserId, int currentUserId) {
        UserPO user = userRepository.findById(targetUserId).orElse(null);
        if (user == null) {
            return null;
        }

        UserRelationVO vo = new UserRelationVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setAvatar(user.getAvatar());
        vo.setGrade(user.getGrade());

        boolean isFollowing = followRepository.findByFollowerIdAndFollowingId(currentUserId, targetUserId).isPresent();
        vo.setIsFollowing(isFollowing);

        boolean isFollowedBy = followRepository.findByFollowerIdAndFollowingId(targetUserId, currentUserId).isPresent();
        vo.setIsFollowedBy(isFollowedBy);

        boolean isFriend = friendRepository.findFriendship(currentUserId, targetUserId).isPresent();
        vo.setIsFriend(isFriend);

        return vo;
    }

    private MessageVO convertToMessageVO(MessagePO message) {
        MessageVO vo = new MessageVO();
        vo.setMessageId(message.getId());
        vo.setSenderId(message.getSenderId());
        vo.setReceiverId(message.getReceiverId());
        vo.setContent(message.getContent());
        vo.setIsRead(message.getIsRead());
        vo.setShareType(message.getShareType());
        vo.setShareId(message.getShareId());
        vo.setCreatedAt(message.getCreatedAt() != null ? message.getCreatedAt().format(FORMATTER) : null);

        UserPO sender = userRepository.findById(message.getSenderId()).orElse(null);
        if (sender != null) {
            vo.setSenderName(sender.getName());
            vo.setSenderAvatar(sender.getAvatar());
        }

        UserPO receiver = userRepository.findById(message.getReceiverId()).orElse(null);
        if (receiver != null) {
            vo.setReceiverName(receiver.getName());
        }

        return vo;
    }

    @Override
    public SocialStatsVO getSocialStats(String userId) {
        int uid = Integer.parseInt(userId);

        SocialStatsVO stats = new SocialStatsVO();
        stats.setFollowingCount(followRepository.countByFollowerId(uid));
        stats.setFollowerCount(followRepository.countByFollowingId(uid));

        List<FriendPO> friends1 = friendRepository.findByUser1IdOrderByCreatedAtDesc(uid);
        List<FriendPO> friends2 = friendRepository.findByUser2IdOrderByCreatedAtDesc(uid);
        stats.setFriendCount(friends1.size() + friends2.size());

        return stats;
    }
}
