package com.minialalipay.user.interfaces.friend;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.user.application.friend.FriendApplicationService;
import com.minialalipay.user.application.friend.dto.FriendDTO;
import com.minialalipay.user.application.friend.dto.FriendRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    private final FriendApplicationService friendService;
    private final RequestIdGenerator requestIdGenerator;

    public FriendController(FriendApplicationService friendService,
                            RequestIdGenerator requestIdGenerator) {
        this.friendService = friendService;
        this.requestIdGenerator = requestIdGenerator;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<FriendRequestDTO>> sendRequest(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid SendRequestBody body,
            HttpServletRequest httpRequest) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        FriendRequestDTO result = friendService.sendRequest(userId, body.toUserId(), body.message());
        return ResponseEntity.ok(ApiResponse.success(result, requestId, traceId));
    }

    @GetMapping("/request/pending")
    public ResponseEntity<ApiResponse<List<FriendRequestDTO>>> listPendingRequests(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest httpRequest) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        List<FriendRequestDTO> result = friendService.listPendingRequests(userId);
        return ResponseEntity.ok(ApiResponse.success(result, requestId, traceId));
    }

    @PostMapping("/request/{requestId}/accept")
    public ResponseEntity<ApiResponse<FriendRequestDTO>> acceptRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String requestId,
            HttpServletRequest httpRequest) {
        String reqId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        FriendRequestDTO result = friendService.acceptRequest(userId, requestId);
        return ResponseEntity.ok(ApiResponse.success(result, reqId, traceId));
    }

    @PostMapping("/request/{requestId}/reject")
    public ResponseEntity<ApiResponse<FriendRequestDTO>> rejectRequest(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String requestId,
            HttpServletRequest httpRequest) {
        String reqId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        FriendRequestDTO result = friendService.rejectRequest(userId, requestId);
        return ResponseEntity.ok(ApiResponse.success(result, reqId, traceId));
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<FriendDTO>>> listFriends(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest httpRequest) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        List<FriendDTO> result = friendService.listFriends(userId);
        return ResponseEntity.ok(ApiResponse.success(result, requestId, traceId));
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<Void>> removeFriend(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String friendUserId,
            HttpServletRequest httpRequest) {
        String requestId = requestIdGenerator.resolve(httpRequest.getHeader("X-Request-Id"));
        String traceId = httpRequest.getHeader("X-Trace-Id");
        friendService.removeFriend(userId, friendUserId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId, traceId));
    }

    public record SendRequestBody(
            @NotBlank(message = "目标用户不能为空") String toUserId,
            String message
    ) {}
}
