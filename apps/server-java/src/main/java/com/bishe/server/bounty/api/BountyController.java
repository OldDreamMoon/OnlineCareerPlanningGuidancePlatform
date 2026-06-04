package com.bishe.server.bounty.api;

import com.bishe.server.bounty.dto.BountyEnterpriseTaskCenterResponse;
import com.bishe.server.bounty.dto.BountySubmissionCreateRequest;
import com.bishe.server.bounty.dto.BountySubmissionCreateResponse;
import com.bishe.server.bounty.dto.BountySubmissionListResponse;
import com.bishe.server.bounty.dto.BountySubmissionReviewRequest;
import com.bishe.server.bounty.dto.BountySubmissionReviewResponse;
import com.bishe.server.bounty.dto.BountyTaskCreateRequest;
import com.bishe.server.bounty.dto.BountyTaskCreateResponse;
import com.bishe.server.bounty.dto.BountyTaskDetailResponse;
import com.bishe.server.bounty.dto.BountyTaskListResponse;
import com.bishe.server.bounty.dto.BountyTaskManageRequest;
import com.bishe.server.bounty.dto.BountyTaskManageResponse;
import com.bishe.server.bounty.dto.BountyTaskUpdateRequest;
import com.bishe.server.bounty.dto.BountyTaskUpdateResponse;
import com.bishe.server.bounty.service.BountyService;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bounty", description = "企业悬赏任务接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/bounty", produces = MediaType.APPLICATION_JSON_VALUE)
public class BountyController {

    private final BountyService bountyService;

    public BountyController(BountyService bountyService) {
        this.bountyService = bountyService;
    }

    @Operation(summary = "发布悬赏任务")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PostMapping(path = "/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BountyTaskCreateResponse> createTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BountyTaskCreateRequest request
    ) {
        // 发布入口只对企业开放，认证门禁和字段落库在 service 内统一处理。
        String traceId = TraceId.next();
        return ApiResponse.ok(bountyService.createTask(principal.getUserId(), request), traceId);
    }

    @Operation(summary = "更新悬赏任务")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PutMapping(path = "/tasks/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BountyTaskUpdateResponse> updateTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long taskId,
            @Valid @RequestBody BountyTaskUpdateRequest request
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(bountyService.updateTask(principal.getUserId(), taskId, request), traceId);
    }

    @Operation(summary = "获取悬赏任务列表")
    @PreAuthorize("hasAnyRole('STUDENT', 'ENTERPRISE', 'ADMIN')")
    @GetMapping(path = "/tasks")
    public ApiResponse<BountyTaskListResponse> listTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be >= 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be >= 1") @Max(value = 50, message = "size must be <= 50") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mineOnly
    ) {
        // 同一列表接口服务学生大厅、企业自查和后台轻量检索，viewer 态在 service 返回前合成。
        return ApiResponse.ok(bountyService.listTasks(principal.getUserId(), principal.getRole(), page, size, keyword, status, mineOnly), TraceId.next());
    }

    @Operation(summary = "获取悬赏任务详情")
    @PreAuthorize("hasAnyRole('STUDENT', 'ENTERPRISE', 'ADMIN')")
    @GetMapping(path = "/tasks/{taskId}")
    public ApiResponse<BountyTaskDetailResponse> getTaskDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long taskId
    ) {
        // 详情入口会按角色返回 mine/mySubmission 等差异字段，前端据此控制动作。
        return ApiResponse.ok(bountyService.getTaskDetail(principal.getUserId(), principal.getRole(), taskId), TraceId.next());
    }

    @Operation(summary = "获取企业任务中心聚合数据")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @GetMapping(path = "/enterprise/task-center")
    public ApiResponse<BountyEnterpriseTaskCenterResponse> getEnterpriseTaskCenter(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        // 企业任务中心使用聚合接口，避免前端逐任务补拉最近提交统计。
        return ApiResponse.ok(bountyService.getEnterpriseTaskCenter(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "提交悬赏成果")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/tasks/{taskId}/submissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BountySubmissionCreateResponse> createSubmission(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long taskId,
            @Valid @RequestBody BountySubmissionCreateRequest request
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(bountyService.createSubmission(principal.getUserId(), taskId, request), traceId);
    }

    @Operation(summary = "获取任务提交列表")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @GetMapping(path = "/tasks/{taskId}/submissions")
    public ApiResponse<BountySubmissionListResponse> listTaskSubmissions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long taskId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be >= 1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be >= 1") @Max(value = 50, message = "size must be <= 50") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String portraitTag,
            @RequestParam(required = false) Long minCommunityScore7d
    ) {
        return ApiResponse.ok(
                bountyService.listTaskSubmissions(principal.getUserId(), taskId, page, size, status, portraitTag, minCommunityScore7d),
                TraceId.next()
        );
    }

    @Operation(summary = "审核学生提交")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PostMapping(path = "/submissions/{submissionId}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BountySubmissionReviewResponse> reviewSubmission(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long submissionId,
            @Valid @RequestBody BountySubmissionReviewRequest request
    ) {
        // 审核入口承载采纳唯一性、自动拒绝其他提交和通知回流。
        String traceId = TraceId.next();
        return ApiResponse.ok(bountyService.reviewSubmission(principal.getUserId(), submissionId, request), traceId);
    }

    @Operation(summary = "管理任务状态")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PostMapping(path = "/tasks/{taskId}/manage", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<BountyTaskManageResponse> manageTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long taskId,
            @Valid @RequestBody BountyTaskManageRequest request
    ) {
        String traceId = TraceId.next();
        return ApiResponse.ok(bountyService.manageTask(principal.getUserId(), taskId, request), traceId);
    }
}
