package com.mapmory.backend.admin.auth;

import com.mapmory.backend.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthService(AdminAccountRepository adminAccountRepository, PasswordEncoder passwordEncoder) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AdminIdentity authenticate(String username, String password) {
        AdminAccount account = adminAccountRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(AdminAuthErrorCode.ADMIN_LOGIN_FAILED));

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new BusinessException(AdminAuthErrorCode.ADMIN_LOGIN_FAILED);
        }

        return new AdminIdentity(account.getId(), account.getUsername());
    }
}
