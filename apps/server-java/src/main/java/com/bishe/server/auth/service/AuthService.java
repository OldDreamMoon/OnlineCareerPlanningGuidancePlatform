package com.bishe.server.auth.service;

import com.bishe.server.auth.SystemUserPolicy;
import com.bishe.server.auth.email.EmailVerificationService;
import com.bishe.server.auth.dto.LoginRequest;
import com.bishe.server.auth.dto.LoginResponse;
import com.bishe.server.auth.dto.MeResponse;
import com.bishe.server.auth.dto.AuthPasswordResetChangeRequest;
import com.bishe.server.auth.dto.RefreshRequest;
import com.bishe.server.auth.dto.RegisterRequest;
import com.bishe.server.auth.dto.RegisterResponse;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeRequest;
import com.bishe.server.profile.dto.StudentProfilePasswordChangeResponse;
import com.bishe.server.profile.dto.StudentProfileSecuritySendCodeResponse;
import com.bishe.server.profile.dto.StudentProfileSecurityVerifyCodeResponse;
import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.dashboard.AdminOperationsDashboardCacheService;
import com.bishe.server.mentor.repository.MentorRepository;
import com.bishe.server.profile.repository.EnterpriseProfileRepository;
import com.bishe.server.profile.service.StudentProfileSecurityService;
import com.bishe.server.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 鉴权核心服务：注册、登录、刷新、当前用户信息。
 */
@Service
public class AuthService {

    private static final String DEFAULT_TIER = "FREE";

    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final StudentProfileSecurityService studentProfileSecurityService;
    private final AdminOperationsDashboardCacheService adminOperationsDashboardCacheService;

    public AuthService(
            UserRepository userRepository,
            MentorRepository mentorRepository,
            EnterpriseProfileRepository enterpriseProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailVerificationService emailVerificationService,
            StudentProfileSecurityService studentProfileSecurityService,
            AdminOperationsDashboardCacheService adminOperationsDashboardCacheService
    ) {
        this.userRepository = userRepository;
        this.mentorRepository = mentorRepository;
        this.enterpriseProfileRepository = enterpriseProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.studentProfileSecurityService = studentProfileSecurityService;
        this.adminOperationsDashboardCacheService = adminOperationsDashboardCacheService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 公开注册入口只接受学生、导师、企业这类自助角色，系统角色不能从这里创建。
        UserRole role;
        try {
            role = UserRole.parse(request.role());
        } catch (Exception ex) {
            throw AuthException.roleNotAllowedForRegister();
        }

        if (!UserRole.canSelfRegister(role)) {
            throw AuthException.roleNotAllowedForRegister();
        }

        String email = request.email().trim().toLowerCase();
        SystemUserPolicy.assertPublicEmailAllowed(email);
        // 邮箱 proof token 是注册第二阶段门禁，避免绕过验证码直接建号。
        emailVerificationService.assertEmailVerificationProof(email, request.emailVerificationToken());
        if (userRepository.findByEmail(email).isPresent()) {
            throw AuthException.emailExists();
        }

        String displayName = request.displayName().trim();
        String realName = StringUtils.hasText(request.realName()) ? request.realName().trim() : displayName;
        String passwordHash = passwordEncoder.encode(request.password());
        long userId = userRepository.save(
                email,
                passwordHash,
                role,
                displayName,
                realName,
                DEFAULT_TIER,
                UserAccountStatus.ACTIVE
        );
        if (role == UserRole.MENTOR) {
            // 导师和企业先生成待认证资料，后续认证审核决定能否进入完整业务链路。
            mentorRepository.createDefaultProfileIfAbsent(userId, displayName, request.companyName(), request.jobTitle(), "PENDING");
        } else if (role == UserRole.ENTERPRISE) {
            enterpriseProfileRepository.createDefaultProfileIfAbsent(userId, request.companyName(), request.jobTitle(), "PENDING");
        }
        if (role == UserRole.STUDENT) {
            // 学生注册会影响后台运营统计，事务内立即失效并安排提交后再失效一次。
            evictOperationsDashboardCache();
        }
        return new RegisterResponse(userId);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 登录只签发 token 和角色真相，具体落到哪个工作台由前端 role 路由分诊。
        String email = request.email().trim().toLowerCase();
        AppUser user = userRepository.findByEmail(email).orElseThrow(AuthException::invalidCredentials);
        SystemUserPolicy.assertInteractiveLoginAllowed(user);
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw AuthException.invalidCredentials();
        }
        if (user.status() != UserAccountStatus.ACTIVE) {
            throw AuthException.accountNotActive();
        }

        userRepository.updateLastLoginAt(user.id(), Instant.now());
        if (user.role() == UserRole.STUDENT) {
            evictOperationsDashboardCache();
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return new LoginResponse(accessToken, refreshToken, user.role().name());
    }

    public LoginResponse refresh(RefreshRequest request) {
        // refresh 接口只接受 refresh token，并采用滚动签发方式同步更新 access/refresh。
        Claims claims = jwtService.parseClaims(request.refreshToken().trim());
        if (!jwtService.isRefreshToken(claims)) {
            throw AuthException.tokenExpiredOrInvalid();
        }

        long userId = jwtService.extractUserId(claims);
        AppUser user = userRepository.findById(userId).orElseThrow(AuthException::tokenExpiredOrInvalid);
        SystemUserPolicy.assertInteractiveSessionAllowed(user);
        if (user.status() != UserAccountStatus.ACTIVE) {
            throw AuthException.accountNotActive();
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return new LoginResponse(accessToken, refreshToken, user.role().name());
    }

    public MeResponse me(long userId) {
        // /auth/me 是前端会话恢复的校准点，返回数据库中的最新角色和展示信息。
        AppUser user = userRepository.findById(userId).orElseThrow(AuthException::tokenExpiredOrInvalid);
        SystemUserPolicy.assertInteractiveSessionAllowed(user);
        if (user.status() != UserAccountStatus.ACTIVE) {
            throw AuthException.accountNotActive();
        }
        return new MeResponse(user.id(), user.role().name(), user.displayName(), user.email());
    }

    public StudentProfileSecuritySendCodeResponse sendPasswordResetCode(String email) {
        AppUser user = requirePasswordResetUser(email);
        // 复用资料安全服务的验证码能力，但邮件场景切到登录找回密码。
        return studentProfileSecurityService.sendPasswordResetCode(
                user.id(),
                StudentProfileSecurityService.PasswordResetEmailScene.LOGIN_RECOVERY
        );
    }

    public StudentProfileSecurityVerifyCodeResponse verifyPasswordResetCode(String email, String code) {
        AppUser user = requirePasswordResetUser(email);
        return studentProfileSecurityService.verifyPasswordResetCode(user.id(), code);
    }

    @Transactional
    public StudentProfilePasswordChangeResponse changePassword(AuthPasswordResetChangeRequest request) {
        AppUser user = requirePasswordResetUser(request.email());
        return studentProfileSecurityService.changePassword(
                user.id(),
                new StudentProfilePasswordChangeRequest(request.passwordResetToken(), request.newPassword())
        );
    }

    private AppUser requirePasswordResetUser(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        AppUser user = userRepository.findByEmail(email).orElseThrow(AuthException::emailNotRegistered);
        // 找回密码不允许系统账号、禁用账号等非交互主体进入改密链路。
        SystemUserPolicy.assertPasswordResetAllowed(user);
        return user;
    }

    private void evictOperationsDashboardCache() {
        // 当前事务内和提交后各失效一次，兼顾即时读取与事务提交后的 Redis 真值。
        adminOperationsDashboardCacheService.evictAllNow();
        adminOperationsDashboardCacheService.evictAllAfterCommit();
    }
}
