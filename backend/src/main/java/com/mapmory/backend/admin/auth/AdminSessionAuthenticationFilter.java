package com.mapmory.backend.admin.auth;

import com.mapmory.backend.admin.account.AdminAccount;
import com.mapmory.backend.admin.account.AdminAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminSessionAuthenticationFilter extends OncePerRequestFilter {

    private final AdminAccountRepository adminAccountRepository;

    public AdminSessionAuthenticationFilter(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (session != null) {
            Object value = session.getAttribute(AdminAuthController.ADMIN_ID_SESSION_KEY);

            if (value instanceof Long adminId) {
                var account = adminAccountRepository.findById(adminId);

                if (account.isPresent()) {
                    AdminAccount admin = account.get();

                    var authentication = new UsernamePasswordAuthenticationToken(
                            admin.getUsername(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    session.invalidate();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

}
