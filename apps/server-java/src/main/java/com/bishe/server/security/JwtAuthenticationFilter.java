package com.bishe.server.security;

import com.bishe.server.auth.SystemUserPolicy;
import com.bishe.server.auth.model.AppUser;
import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.repository.UserRepository;
import com.bishe.server.auth.service.AuthException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 解析 Bearer Token 并注入 SecurityContext。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            // 没有 Bearer token 时先放行，最终是否 401 交给 Spring Security 规则判断。
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parseClaims(token);
            if (!jwtService.isAccessToken(claims)) {
                // 业务接口只接受 access token，refresh token 只能走刷新入口。
                throw AuthException.tokenExpiredOrInvalid();
            }

            long userId = jwtService.extractUserId(claims);
            AppUser user = userRepository.findById(userId).orElseThrow(AuthException::tokenExpiredOrInvalid);
            // token 有效后仍查库校验账号状态，防止禁用用户继续复用旧 token。
            SystemUserPolicy.assertInteractiveSessionAllowed(user);
            if (user.status() != UserAccountStatus.ACTIVE) {
                throw AuthException.accountNotActive();
            }

            UserPrincipal principal = UserPrincipal.from(user);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            // 写入 SecurityContext 后，控制器上的 @PreAuthorize 才能拿到角色权限。
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            // 解析失败只清理上下文，统一 401 响应由认证入口点生成。
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
