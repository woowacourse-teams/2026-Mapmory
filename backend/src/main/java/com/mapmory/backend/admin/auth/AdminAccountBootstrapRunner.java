package com.mapmory.backend.admin.auth;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminAccountBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrapRunner.class);

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminAccountBootstrapRunner(
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.bootstrap.username:}") String username,
            @Value("${admin.bootstrap.password:}") String password
    ) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("관리자 최초 등록에는 ADMIN_BOOTSTRAP_USERNAME과 ADMIN_BOOTSTRAP_PASSWORD가 모두 필요합니다.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("관리자 비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }

        if (adminAccountRepository.findByUsername(username).isPresent()) {
            log.info("Bootstrap administrator already exists; no account changes were made.");
            return;
        }

        adminAccountRepository.save(AdminAccount.create(username, passwordEncoder.encode(password)));
        log.info("Bootstrap administrator created.");
    }
}
