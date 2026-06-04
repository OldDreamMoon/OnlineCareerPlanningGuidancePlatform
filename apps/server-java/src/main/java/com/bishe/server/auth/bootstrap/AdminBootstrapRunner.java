package com.bishe.server.auth.bootstrap;

import com.bishe.server.auth.model.UserAccountStatus;
import com.bishe.server.auth.model.UserRole;
import com.bishe.server.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时确保管理员账号存在，避免管理端无法登录。
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminInitProperties adminInitProperties;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AdminInitProperties adminInitProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminInitProperties = adminInitProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminInitProperties.getEmail()) || !StringUtils.hasText(adminInitProperties.getPassword())) {
            return;
        }

        String email = adminInitProperties.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        userRepository.save(
                email,
                passwordEncoder.encode(adminInitProperties.getPassword()),
                UserRole.ADMIN,
                adminInitProperties.getDisplayName(),
                adminInitProperties.getDisplayName(),
                "PREMIUM",
                UserAccountStatus.ACTIVE
        );
        log.info("initialized admin account: {}", email);
    }
}
