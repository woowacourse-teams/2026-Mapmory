package com.mapmory.backend.admin.auth;

import com.mapmory.backend.admin.account.AdminAccount;
import com.mapmory.backend.admin.account.AdminAccountRepository;
import com.mapmory.backend.admin.auth.dto.AdminLoginRequest;
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
    public AdminAccount authenticate(AdminLoginRequest request) {
        AdminAccount account = adminAccountRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(AdminAuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException(AdminAuthErrorCode.INVALID_CREDENTIALS);
        }

        return account;
    }
}
