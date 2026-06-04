package com.bishe.server.profile.api;

import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.profile.dto.EnterpriseProfileLogoUploadResponse;
import com.bishe.server.profile.dto.EnterpriseProfileResponse;
import com.bishe.server.profile.dto.EnterpriseProfileUpdateRequest;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeRequest;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeResponse;
import com.bishe.server.profile.dto.StudentProfileSecuritySendCodeResponse;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeRequest;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeResponse;
import com.bishe.server.profile.service.EnterpriseLogoStorageService;
import com.bishe.server.profile.service.EnterpriseProfileService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 企业认证资料接口。
 */
@Tag(name = "Enterprise Profile", description = "企业认证资料接口")
@Validated
@RestController
@RequestMapping(path = "/api/v1/profiles/enterprises", produces = MediaType.APPLICATION_JSON_VALUE)
public class EnterpriseProfileController {

    private final EnterpriseProfileService enterpriseProfileService;
    private final StudentProfileSecurityService profileSecurityService;

    public EnterpriseProfileController(
            EnterpriseProfileService enterpriseProfileService,
            StudentProfileSecurityService profileSecurityService
    ) {
        this.enterpriseProfileService = enterpriseProfileService;
        this.profileSecurityService = profileSecurityService;
    }

    @Operation(summary = "获取我的企业认证资料")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @GetMapping(path = "/me")
    public ApiResponse<EnterpriseProfileResponse> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        // 企业资料页读取资料主体，认证 submission 由 /certification/me 单独提供。
        EnterpriseProfileResponse data = enterpriseProfileService.getMyProfile(principal.getUserId());
        return ApiResponse.ok(data, TraceId.next());
    }

    @Operation(summary = "更新我的企业认证资料")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PutMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<EnterpriseProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EnterpriseProfileUpdateRequest request
    ) {
        EnterpriseProfileResponse data = enterpriseProfileService.updateMyProfile(principal.getUserId(), request);
        return ApiResponse.ok("profile updated", data, TraceId.next());
    }

    @Operation(summary = "上传或替换我的企业 Logo")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PostMapping(path = "/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EnterpriseProfileLogoUploadResponse> uploadMyLogo(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        // Logo 上传会返回带版本戳的公开 URL，前端用它刷新缓存图像。
        EnterpriseProfileLogoUploadResponse data = enterpriseProfileService.uploadMyLogo(principal.getUserId(), file);
        return ApiResponse.ok("logo uploaded", data, TraceId.next());
    }

    @Operation(summary = "向当前邮箱发送企业密码重置验证码")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PostMapping(path = "/me/security/password/send-code")
    public ApiResponse<StudentProfileSecuritySendCodeResponse> sendPasswordResetCode(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        StudentProfileSecuritySendCodeResponse data = profileSecurityService.sendPasswordResetCode(principal.getUserId());
        return ApiResponse.ok("security code sent", data, TraceId.next());
    }

    @Operation(summary = "校验企业密码重置验证码")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PostMapping(path = "/me/security/password/verify-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileSecurityVerifyCodeResponse> verifyPasswordResetCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfileSecurityVerifyCodeRequest request
    ) {
        StudentProfileSecurityVerifyCodeResponse data = profileSecurityService.verifyPasswordResetCode(principal.getUserId(), request.code());
        return ApiResponse.ok("security code verified", data, TraceId.next());
    }

    @Operation(summary = "修改企业登录密码")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @PostMapping(path = "/me/security/password/change", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfilePasswordChangeResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudentProfilePasswordChangeRequest request
    ) {
        StudentProfilePasswordChangeResponse data = profileSecurityService.changePassword(principal.getUserId(), request);
        return ApiResponse.ok("password changed", data, TraceId.next());
    }

    @Operation(summary = "获取我的企业 Logo")
    @PreAuthorize("hasRole('ENTERPRISE')")
    @GetMapping(path = "/me/logo", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> getMyLogo(@AuthenticationPrincipal UserPrincipal principal) {
        EnterpriseLogoStorageService.StoredLogoContent content = enterpriseProfileService.getMyLogoContent(principal.getUserId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.bytes());
    }

    @Operation(summary = "获取企业公开 Logo")
    @GetMapping(path = "/{enterpriseUserId}/logo", produces = MediaType.ALL_VALUE)
    public ResponseEntity<byte[]> getPublicLogo(@PathVariable long enterpriseUserId) {
        EnterpriseLogoStorageService.StoredLogoContent content = enterpriseProfileService.getPublicLogoContent(enterpriseUserId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.bytes());
    }
}
