package com.bishe.server.certification.api;

import com.bishe.server.certification.dto.CertificationReviewDecisionRequest;
import com.bishe.server.certification.dto.CertificationReviewDetailResponse;
import com.bishe.server.certification.dto.CertificationReviewListResponse;
import com.bishe.server.certification.service.CertificationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员认证审核工作区接口。
 */
@Tag(name = "AdminCertificationReviews", description = "管理员认证审核工作区接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminCertificationReviewController {

    private final CertificationService certificationService;

    public AdminCertificationReviewController(CertificationService certificationService) {
        this.certificationService = certificationService;
    }

    @Operation(summary = "查询认证审核工作区列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/certification-reviews")
    public ApiResponse<CertificationReviewListResponse> getReviewList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status
    ) {
        CertificationReviewListResponse data = certificationService.getReviewList(page, size, keyword, role, status);
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "查询指定用户认证审核详情")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(path = "/{userId}/certification-review")
    public ApiResponse<CertificationReviewDetailResponse> getReviewDetail(@PathVariable long userId) {
        CertificationReviewDetailResponse data = certificationService.getReviewDetail(userId);
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "审核指定用户当前认证提交")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{userId}/certification-review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<CertificationReviewDetailResponse> reviewCurrentSubmission(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long userId,
            @Valid @RequestBody CertificationReviewDecisionRequest request
    ) {
        CertificationReviewDetailResponse data = certificationService.reviewCurrentSubmission(
                principal.getUserId(),
                userId,
                request.approvalStatus(),
                request.reviewNote()
        );
        return ApiResponse.ok("certification reviewed", data, TraceId.next());
    }
}
