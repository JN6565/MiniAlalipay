package com.minialalipay.user.application.friend;

import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.user.application.friend.dto.FriendDTO;
import com.minialalipay.user.application.friend.dto.FriendRequestDTO;
import com.minialalipay.user.domain.friend.Friend;
import com.minialalipay.user.domain.friend.FriendRepository;
import com.minialalipay.user.domain.friend.FriendRequest;
import com.minialalipay.user.domain.friend.FriendRequestRepository;
import com.minialalipay.user.domain.user.User;
import com.minialalipay.user.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FriendApplicationService {

    private final FriendRequestRepository requestRepository;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    public FriendApplicationService(FriendRequestRepository requestRepository,
                                     FriendRepository friendRepository,
                                     UserRepository userRepository) {
        this.requestRepository = requestRepository;
        this.friendRepository = friendRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FriendRequestDTO sendRequest(String fromUserId, String toUserId, String message) {
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        // 检查目标用户是否存在
        if (userRepository.findById(toUserId).isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        // 检查是否已经是好友
        if (friendRepository.areFriends(fromUserId, toUserId)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        // 检查是否有待处理的请求
        Optional<FriendRequest> existing = requestRepository.findPendingBetween(fromUserId, toUserId);
        if (existing.isPresent()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        // 检查对方是否已经向自己发过请求（自动接受）
        Optional<FriendRequest> reverseRequest = requestRepository.findPendingBetween(toUserId, fromUserId);
        if (reverseRequest.isPresent()) {
            FriendRequest req = reverseRequest.get();
            req.accept(Instant.now());
            requestRepository.update(req);
            addFriendship(fromUserId, toUserId);
            return toRequestDTO(req, fromUserId);
        }

        // 创建新请求
        String requestId = generateId();
        Instant now = Instant.now();
        FriendRequest request = new FriendRequest(requestId, fromUserId, toUserId, message, now);
        requestRepository.save(request);
        return toRequestDTO(request, fromUserId);
    }

    @Transactional
    public FriendRequestDTO acceptRequest(String userId, String requestId) {
        FriendRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (!request.getToUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        if (!request.isPending()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        request.accept(Instant.now());
        requestRepository.update(request);
        addFriendship(request.getFromUserId(), request.getToUserId());
        return toRequestDTO(request, userId);
    }

    @Transactional
    public FriendRequestDTO rejectRequest(String userId, String requestId) {
        FriendRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (!request.getToUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        if (!request.isPending()) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        request.reject(Instant.now());
        requestRepository.update(request);
        return toRequestDTO(request, userId);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestDTO> listPendingRequests(String userId) {
        return requestRepository.findPendingByToUserId(userId, 50).stream()
                .map(r -> toRequestDTO(r, userId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendDTO> listFriends(String userId) {
        return friendRepository.findByUserId(userId, 200).stream()
                .map(f -> toFriendDTO(f))
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeFriend(String userId, String friendUserId) {
        if (!friendRepository.areFriends(userId, friendUserId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        friendRepository.delete(userId, friendUserId);
    }

    private void addFriendship(String userId1, String userId2) {
        Instant now = Instant.now();
        friendRepository.save(new Friend(userId1, userId2, now));
        friendRepository.save(new Friend(userId2, userId1, now));
    }

    private FriendRequestDTO toRequestDTO(FriendRequest r, String currentUserId) {
        String fromUserName = lookupUserName(r.getFromUserId());
        return new FriendRequestDTO(
                r.getRequestId(),
                r.getFromUserId(),
                fromUserName,
                r.getToUserId(),
                r.getStatus().name(),
                r.getMessage(),
                r.getCreatedAt()
        );
    }

    private FriendDTO toFriendDTO(Friend f) {
        String friendName = lookupUserName(f.getFriendUserId());
        String accountNumber = lookupAccountNumber(f.getFriendUserId());
        return new FriendDTO(
                f.getFriendUserId(),
                friendName,
                accountNumber,
                f.getAlias(),
                f.getCreatedAt()
        );
    }

    private String lookupUserName(String userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) return "未知用户";
        User u = user.get();
        String realName = u.getRealName();
        if (realName != null && !realName.isBlank()) return realName;
        String nickname = u.getNickname();
        if (nickname != null && !nickname.isBlank()) return nickname;
        return "未知用户";
    }

    private String lookupAccountNumber(String userId) {
        return userRepository.findById(userId).map(User::getAccountNumber).orElse("");
    }

    private String generateId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 26; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }
}
