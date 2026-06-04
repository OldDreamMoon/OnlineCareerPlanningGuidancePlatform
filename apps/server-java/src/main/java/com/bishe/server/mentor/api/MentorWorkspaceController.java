package com.bishe.server.mentor.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.mentor.dto.MentorDashboardResponse;
import com.bishe.server.mentor.dto.MentorOwnProfileResponse;
import com.bishe.server.mentor.dto.MentorProfileAvatarUploadResponse;
import com.bishe.server.mentor.dto.MentorProfileUpdateRequest;
import com.bishe.server.mentor.service.MentorAvatarService;
import com.bishe.server.mentor.service.MentorWorkspaceService;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeRequest;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeResponse;
import com.bishe.server.profile.dto.StudentProfileSecuritySendCodeResponse;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeRequest;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeResponse;
import com.bishe.server.profile.service.StudentProfileSecurityService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 导师工作台接口：主页概览与资料维护。
 */
@Tag(name = "MentorWorkspace", description = "导师工作台接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/mentor", produces = MediaType.APPLICATION_JSON_VALUE)
public class MentorWorkspaceController {

    private final MentorWorkspaceService mentorWorkspaceService;
    private final MentorAvatarService mentorAvatarService;
    private final StudentProfileSecurityService profileSecurityService;

    public MentorWorkspaceController(
            MentorWorkspaceService mentorWorkspaceService,
            MentorAvatarService mentorAvatarService,
            StudentProfileSecurityService profileSecurityService
    ) {
        this.mentorWorkspaceService = mentorWorkspaceService;
        this.mentorAvatarService = mentorAvatarService;
        this.profileSecurityService = profileSecurityService;
    }

    @Operation(summary = "获取导师工作台概览")
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping(path = "/dashboard")
    public ApiResponse<MentorDashboardResponse> getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        // 导师首页只返回聚合摘要和最近订单，详情仍走咨询订单接口。
        return ApiResponse.ok(mentorWorkspaceService.getDashboard(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "获取导师本人资料")
    @PreAuthorize("hasRole('MENTOR')")
    @GetMapping(path = "/profile")
    public ApiResponse<MentorOwnProfileResponse> getOwnProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(mentorWorkspaceService.getOwnProfile(principal.getUserId()), TraceId.next());
    }

    @Operation(summary = "更新导师本人资料")
    @PreAuthorize("hasRole('MENTOR')")
    @PutMapping(path = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MentorOwnProfileResponse> updateOwnProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorProfileUpdateRequest request
    ) {
        // 资料、服务场景、套餐与可接单开关作为一个提交事务处理。
        return ApiResponse.ok(mentorWorkspaceService.updateOwnProfile(principal.getUserId(), request), TraceId.next());
    }

    @Operation(summary = "上传或替换导师头像")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MentorProfileAvatarUploadResponse> uploadOwnAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.ok("avatar uploaded", mentorAvatarService.uploadOwnAvatar(principal.getUserId(), file), TraceId.next());
    }

    @Operation(summary = "向当前邮箱发送导师密码重置验证码")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/profile/security/password/send-code")
    public ApiResponse<StudentProfileSecuritySendCodeResponse> sendPasswordResetCode(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        // 导师资料页改密复用学生资料安全服务的验证码和 proof token 机制。
        StudentProfileSecuritySendCodeResponse data = profileSecurityService.sendPasswordResetCode(principal.getUserId());
        return ApiResponse.ok("security code sent", data, TraceId.next());
    }

    @Operation(summary = "校验导师密码重置验证码")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/profile/security/password/verify-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileSecurityVerifyCodeResponse> verifyPasswordResetCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileSecurityVerifyCodeRequest request
    ) {
        StudentProfileSecurityVerifyCodeResponse data = profileSecurityService.verifyPasswordResetCode(principal.getUserId(), request.code());
        return ApiResponse.ok("security code verified", data, TraceId.next());
    }

    @Operation(summary = "修改导师登录密码")
    @PreAuthorize("hasRole('MENTOR')")
    @PostMapping(path = "/profile/security/password/change", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfilePasswordChangeResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfilePasswordChangeRequest request
    ) {
        StudentProfilePasswordChangeResponse data = profileSecurityService.changePassword(principal.getUserId(), request);
        return ApiResponse.ok("password changed", data, TraceId.next());
    }
}
