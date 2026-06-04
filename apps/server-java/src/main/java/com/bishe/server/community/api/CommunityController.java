package com.bishe.server.community.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.community.dto.CommunityCommentCreateRequest;
import com.bishe.server.community.dto.CommunityCommentCreateResponse;
import com.bishe.server.community.dto.CommunityLeaderboardResponse;
import com.bishe.server.community.dto.CommunityLikeResponse;
import com.bishe.server.community.dto.CommunityPostCreateRequest;
import com.bishe.server.community.dto.CommunityPostCreateResponse;
import com.bishe.server.community.dto.CommunityPostDetailResponse;
import com.bishe.server.community.dto.CommunityPostListResponse;
import com.bishe.server.community.dto.CommunityPostStatusUpdateRequest;
import com.bishe.server.community.dto.CommunityPostStatusUpdateResponse;
import com.bishe.server.community.service.CommunityService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区接口：帖子、评论、点赞与贡献榜。
 */
@Tag(name = "Community", description = "社区帖子、评论、点赞与贡献榜接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/community", produces = MediaType.APPLICATION_JSON_VALUE)
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @Operation(summary = "创建帖子")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR')")
    @PostMapping(path = "/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CommunityPostCreateResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CommunityPostCreateRequest request
    ) {
        String traceId = TraceId.next();
        // 发帖会先走内容治理，REVIEW 时记录已入库但不进公开 feed。
        CommunityPostCreateResponse data = communityService.createPost(traceId, principal.getUserId(), request);
        return ApiResponse.ok(data, traceId);
    }

    @Operation(summary = "获取帖子列表")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR', 'ADMIN')")
    @GetMapping(path = "/posts")
    public ApiResponse<CommunityPostListResponse> listPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be >= 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be >= 1") @Max(value = 50, message = "size must be <= 50") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String scenarioCode
    ) {
        // 社区列表只返回公开可见内容，作者自己的待审内容由详情/个人记录链路处理。
        CommunityPostListResponse data = communityService.listPosts(principal.getUserId(), page, size, keyword, tag, scenarioCode);
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "获取帖子详情")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR', 'ADMIN')")
    @GetMapping(path = "/posts/{postId}")
    public ApiResponse<CommunityPostDetailResponse> getPostDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long postId
    ) {
        // 详情按角色决定管理员只读、作者态和互动状态，前端不自行放权。
        CommunityPostDetailResponse data = communityService.getPostDetail(principal.getUserId(), principal.getRole(), postId);
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "创建评论")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR')")
    @PostMapping(path = "/posts/{postId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CommunityCommentCreateResponse> createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long postId,
            @Valid @RequestBody CommunityCommentCreateRequest request
    ) {
        String traceId = TraceId.next();
        // 回复同样走治理链，AI 首评和用户评论在 service 内区分处理。
        CommunityCommentCreateResponse data = communityService.createComment(traceId, principal.getUserId(), postId, request);
        return ApiResponse.ok(data, traceId);
    }

    @Operation(summary = "更新帖子解决状态")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR')")
    @PostMapping(path = "/posts/{postId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CommunityPostStatusUpdateResponse> updatePostStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long postId,
            @Valid @RequestBody CommunityPostStatusUpdateRequest request
    ) {
        CommunityPostStatusUpdateResponse data = communityService.updatePostStatus(principal.getUserId(), postId, request);
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "点赞帖子")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR')")
    @PostMapping(path = "/posts/{postId}/like")
    public ApiResponse<CommunityLikeResponse> likePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long postId
    ) {
        CommunityLikeResponse data = communityService.likePost(principal.getUserId(), postId);
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "取消点赞")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR')")
    @DeleteMapping(path = "/posts/{postId}/like")
    public ApiResponse<CommunityLikeResponse> unlikePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long postId
    ) {
        CommunityLikeResponse data = communityService.unlikePost(principal.getUserId(), postId);
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "获取社区 7 日贡献榜")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR', 'ENTERPRISE', 'ADMIN')")
    @GetMapping(path = "/leaderboard")
    public ApiResponse<CommunityLeaderboardResponse> getLeaderboard(
            @RequestParam(defaultValue = "7d") String window,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be >= 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be >= 1") @Max(value = 50, message = "size must be <= 50") int size
    ) {
        // 贡献榜当前以学生社区行为为统计主体，导师侧只是查看入口。
        return ApiResponse.ok(communityService.getLeaderboard(window, page, size), TraceId.next());
    }
}
