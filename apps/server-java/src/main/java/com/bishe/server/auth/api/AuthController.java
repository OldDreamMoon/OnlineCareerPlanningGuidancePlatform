package com.bishe.server.auth.api;

import com.bishe.server.auth.captcha.GeetestCaptchaService;
import com.bishe.server.auth.dto.AuthCaptchaConfigResponse;
import com.bishe.server.auth.dto.AuthPasswordResetChangeRequest;
import com.bishe.server.auth.dto.AuthPasswordResetSendCodeRequest;
import com.bishe.server.auth.dto.AuthPasswordResetVerifyCodeRequest;
import com.bishe.server.auth.dto.GeetestVerifyRequest;
import com.bishe.server.auth.dto.GeetestVerifyResponse;
import com.bishe.server.auth.dto.LoginRequest;
import com.bishe.server.auth.dto.LoginResponse;
import com.bishe.server.auth.dto.MeResponse;
import com.bishe.server.auth.dto.RefreshRequest;
import com.bishe.server.auth.dto.RegisterRequest;
import com.bishe.server.auth.dto.RegisterResponse;
import com.bishe.server.auth.dto.RegisterWithCertificationResponse;
import com.bishe.server.auth.dto.SendEmailVerificationCodeRequest;
import com.bishe.server.auth.dto.SendEmailVerificationCodeResponse;
import com.bishe.server.auth.dto.VerifyEmailVerificationCodeRequest;
import com.bishe.server.auth.dto.VerifyEmailVerificationCodeResponse;
import com.bishe.server.auth.email.EmailVerificationService;
import com.bishe.server.auth.service.AuthService;
import com.bishe.server.certification.service.CertificationService;
import com.bishe.server.common.ApiResponse;
import com.bishe.server.common.TraceId;
import com.bishe.server.demo.DemoModeProperties;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeResponse;
import com.bishe.server.profile.dto.StudentProfileSecuritySendCodeResponse;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeResponse;
import com.bishe.server.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 鉴权接口：register/login/refresh/me。
 */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthService authService;
    private final CertificationService certificationService;
    private final GeetestCaptchaService geetestCaptchaService;
    private final EmailVerificationService emailVerificationService;
    private final DemoModeProperties demoModeProperties;

    public AuthController(
            AuthService authService,
            CertificationService certificationService,
            GeetestCaptchaService geetestCaptchaService,
            EmailVerificationService emailVerificationService,
            DemoModeProperties demoModeProperties
    ) {
        this.authService = authService;
        this.certificationService = certificationService;
        this.geetestCaptchaService = geetestCaptchaService;
        this.emailVerificationService = emailVerificationService;
        this.demoModeProperties = demoModeProperties;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        // 普通注册只开放学生自助建号，导师/企业走带认证材料的入口。
        RegisterResponse data = authService.register(request);
        return ApiResponse.ok("registered", data, TraceId.next());
    }

    @PostMapping(path = "/register-with-certification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RegisterWithCertificationResponse> registerWithCertification(
            @RequestParam String role,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String displayName,
            @RequestParam(required = false) String realName,
            @RequestParam String companyName,
            @RequestParam String jobTitle,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String emailVerificationToken
    ) {
        // 导师/企业注册和首次认证合并提交，文件材料直接进入认证版本链。
        RegisterWithCertificationResponse data = certificationService.registerWithCertification(
                role,
                email,
                password,
                displayName,
                realName,
                companyName,
                jobTitle,
                file,
                emailVerificationToken
        );
        return ApiResponse.ok("registered", data, TraceId.next());
    }

    @GetMapping(path = "/captcha/config")
    public ApiResponse<AuthCaptchaConfigResponse> getCaptchaConfig() {
        AuthCaptchaConfigResponse data = geetestCaptchaService.getPublicConfig();
        return ApiResponse.ok(data, TraceId.next());
    }

    @PostMapping(path = "/captcha/geetest/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<GeetestVerifyResponse> verifyGeetest(@Valid @RequestBody GeetestVerifyRequest request) {
        // Geetest 校验成功后签短 proof token，后续发邮箱码或找回密码复用。
        GeetestVerifyResponse data = geetestCaptchaService.verifyAndIssueToken(request);
        return ApiResponse.ok("captcha verified", data, TraceId.next());
    }

    @PostMapping(path = "/email/send-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SendEmailVerificationCodeResponse> sendEmailVerificationCode(@Valid @RequestBody SendEmailVerificationCodeRequest request) {
        SendEmailVerificationCodeResponse data = emailVerificationService.sendCode(request.email(), request.captchaVerificationToken());
        return ApiResponse.ok("email verification code sent", data, TraceId.next());
    }

    @PostMapping(path = "/email/verify-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<VerifyEmailVerificationCodeResponse> verifyEmailVerificationCode(@Valid @RequestBody VerifyEmailVerificationCodeRequest request) {
        // 邮箱验证码换 proof token，注册第二阶段必须带 proof 令牌。
        VerifyEmailVerificationCodeResponse data = emailVerificationService.verifyCode(request.email(), request.code());
        return ApiResponse.ok("email verification code verified", data, TraceId.next());
    }

    @PostMapping(path = "/password/reset/send-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileSecuritySendCodeResponse> sendPasswordResetCode(
            @Valid @RequestBody AuthPasswordResetSendCodeRequest request
    ) {
        // 找回密码发码前也要求 Geetest proof，避免公开邮箱接口被滥用。
        if (!demoModeProperties.isPasswordResetBypassEnabled()) {
            geetestCaptchaService.assertRegistrationProof(request.email(), request.captchaVerificationToken());
        }
        StudentProfileSecuritySendCodeResponse data = authService.sendPasswordResetCode(request.email());
        return ApiResponse.ok("password reset code sent", data, TraceId.next());
    }

    @PostMapping(path = "/password/reset/verify-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfileSecurityVerifyCodeResponse> verifyPasswordResetCode(
            @Valid @RequestBody AuthPasswordResetVerifyCodeRequest request
    ) {
        StudentProfileSecurityVerifyCodeResponse data = authService.verifyPasswordResetCode(request.email(), request.code());
        return ApiResponse.ok("password reset code verified", data, TraceId.next());
    }

    @PostMapping(path = "/password/reset/change", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<StudentProfilePasswordChangeResponse> changePassword(
            @Valid @RequestBody AuthPasswordResetChangeRequest request
    ) {
        StudentProfilePasswordChangeResponse data = authService.changePassword(request);
        return ApiResponse.ok("password reset changed", data, TraceId.next());
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 登录只签发 token 和基础 session 信息，角色分诊交给前端路由层。
        LoginResponse data = authService.login(request);
        return ApiResponse.ok("logged in", data, TraceId.next());
    }

    @PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        // refresh 入口只接受 refresh token，access token 失效由前端 apiClient 兜底触发。
        LoginResponse data = authService.refresh(request);
        return ApiResponse.ok("refreshed", data, TraceId.next());
    }

    @GetMapping(path = "/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        // /auth/me 是浏览器刷新后的会话真相源，返回当前账号状态和角色资料。
        MeResponse data = authService.me(principal.getUserId());
        return ApiResponse.ok(data, TraceId.next());
    }
}
