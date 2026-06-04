package com.bishe.server.profile.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.profile.dto.StudentProfileAvatarUploadResponse;
import com.bishe.server.profile.dto.StudentProfileEmailChangeRequest;
import com.bishe.server.profile.dto.StudentProfileEmailChangeResponse;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeRequest;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeResponse;
import com.bishe.server.profile.dto.StudentPublicProfileResponse;
import com.bishe.server.profile.dto.StudentProfilePrivacyUpdateRequest;
import com.bishe.server.profile.dto.StudentProfilePrivacyUpdateResponse;
import com.bishe.server.profile.dto.StudentProfileResponse;
import com.bishe.server.profile.dto.StudentProfileSecurityEmailSendCodeRequest;
import com.bishe.server.profile.dto.StudentProfileSecuritySendCodeResponse;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeRequest;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeResponse;
import com.bishe.server.profile.dto.StudentProfileUpdateRequest;
import com.bishe.server.profile.dto.StudentProfileUpdateResponse;
import com.bishe.server.profile.service.StudentAvatarStorageService;
import com.bishe.server.profile.service.StudentProfileService;
import com.bishe.server.profile.service.StudentProfileSecurityService;
import com.bishe.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生个人资料与画像接口。
 */
@Tag(name = "Student Profile", description = "学生冷启动资料与动态画像接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/profiles/students", produces = MediaType.APPLICATION_JSON_VALUE)
public class StudentProfileController {

    private final StudentProfileService studentProfileService;
    private final StudentProfileSecurityService studentProfileSecurityService;

    public StudentProfileController(
            StudentProfileService studentProfileService,
            StudentProfileSecurityService studentProfileSecurityService
    ) {
        this.studentProfileService = studentProfileService;
        this.studentProfileSecurityService = studentProfileSecurityService;
    }

    @Operation(summary = "获取我的学生画像")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/me")
    public ApiResponse<StudentProfileResponse> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        // 资料中心读取完整资料、隐私矩阵和画像快照。
        StudentProfileResponse data = studentProfileService.getMyProfile(principal.getUserId());
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "获取学生个人空间资料")
    @GetMapping(path = "/{studentUserId}/public")
    public ApiResponse<StudentPublicProfileResponse> getPublicProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long studentUserId
    ) {
        // 公开空间允许登录后多角色访问，具体字段可见性由 Service 根据 viewer 裁剪。
        StudentPublicProfileResponse data = studentProfileService.getPublicProfile(studentUserId, principal);
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "更新我的学生资料")
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileUpdateResponse> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileUpdateRequest request
    ) {
        // 更新资料会触发画像刷新和公开空间缓存失效。
        StudentProfileUpdateResponse data = studentProfileService.updateMyProfile(principal.getUserId(), request);
        return ApiResponse.ok("profile updated", data, TraceId.next());
    }

    @Operation(summary = "上传或替换我的学生头像")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StudentProfileAvatarUploadResponse> uploadMyAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        StudentProfileAvatarUploadResponse data = studentProfileService.uploadMyAvatar(principal.getUserId(), file);
        return ApiResponse.ok("avatar uploaded", data, TraceId.next());
    }

    @Operation(summary = "获取我的学生头像")
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping(path = "/me/avatar", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> getMyAvatar(@AuthenticationPrincipal UserPrincipal principal) {
        StudentAvatarStorageService.StoredAvatarContent content = studentProfileService.getMyAvatarContent(principal.getUserId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.bytes());
    }

    @Operation(summary = "获取学生公开头像")
    @GetMapping(path = "/{studentUserId}/avatar", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> getPublicAvatar(@PathVariable long studentUserId) {
        StudentAvatarStorageService.StoredAvatarContent content = studentProfileService.getPublicAvatarContent(studentUserId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.bytes());
    }

    @Operation(summary = "更新我的隐私矩阵")
    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping(path = "/me/privacy", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfilePrivacyUpdateResponse> updateMyPrivacy(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfilePrivacyUpdateRequest request
    ) {
        // 隐私矩阵只负责公开空间字段裁剪，不改画像事实本身。
        StudentProfilePrivacyUpdateResponse data = studentProfileService.updateMyPrivacy(principal.getUserId(), request);
        return ApiResponse.ok("privacy updated", data, TraceId.next());
    }

    @Operation(summary = "向当前邮箱或新邮箱发送验证码")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/me/security/email/send-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileSecuritySendCodeResponse> sendEmailSecurityCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileSecurityEmailSendCodeRequest request
    ) {
        // 邮箱换绑分 CURRENT 和 NEW 两段验证码，避免只验证新邮箱就改号。
        StudentProfileSecuritySendCodeResponse data = studentProfileSecurityService.sendEmailCode(principal.getUserId(), request);
        return ApiResponse.ok("security code sent", data, TraceId.next());
    }

    @Operation(summary = "校验当前邮箱验证码")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/me/security/email/verify-current-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileSecurityVerifyCodeResponse> verifyCurrentEmailCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileSecurityVerifyCodeRequest request
    ) {
        StudentProfileSecurityVerifyCodeResponse data = studentProfileSecurityService.verifyCurrentEmailCode(principal.getUserId(), request.code());
        return ApiResponse.ok("security code verified", data, TraceId.next());
    }

    @Operation(summary = "完成邮箱换绑")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/me/security/email/change", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileEmailChangeResponse> changeEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileEmailChangeRequest request
    ) {
        StudentProfileEmailChangeResponse data = studentProfileSecurityService.changeEmail(principal.getUserId(), request);
        return ApiResponse.ok("email changed", data, TraceId.next());
    }

    @Operation(summary = "向当前邮箱发送密码重置验证码")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/me/security/password/send-code")
    public ApiResponse<StudentProfileSecuritySendCodeResponse> sendPasswordResetCode(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        StudentProfileSecuritySendCodeResponse data = studentProfileSecurityService.sendPasswordResetCode(principal.getUserId());
        return ApiResponse.ok("security code sent", data, TraceId.next());
    }

    @Operation(summary = "校验密码重置验证码")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/me/security/password/verify-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileSecurityVerifyCodeResponse> verifyPasswordResetCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileSecurityVerifyCodeRequest request
    ) {
        StudentProfileSecurityVerifyCodeResponse data = studentProfileSecurityService.verifyPasswordResetCode(principal.getUserId(), request.code());
        return ApiResponse.ok("security code verified", data, TraceId.next());
    }

    @Operation(summary = "修改登录密码")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/me/security/password/change", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfilePasswordChangeResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfilePasswordChangeRequest request
    ) {
        StudentProfilePasswordChangeResponse data = studentProfileSecurityService.changePassword(principal.getUserId(), request);
        return ApiResponse.ok("password changed", data, TraceId.next());
    }
}
