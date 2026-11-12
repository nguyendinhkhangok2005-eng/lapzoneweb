package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.entity.User;
import com.lapzone.lapzoneweb.model.repository.UserRepository;
import com.lapzone.lapzoneweb.security.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class OrderAuthorization {
    private final UserRepository users;
    public OrderAuthorization(UserRepository users) { this.users = users; }

    public User requireUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails details)
                || details.getUser().getId() == null)
            throw new AccessDeniedException("Vui lòng đăng nhập để xem đơn hàng.");
        return users.findById(details.getUser().getId())
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không còn khả dụng."));
    }

    public User requireAdmin() {
        User user = requireUser();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!"ADMIN".equals(user.getRole()) || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw new AccessDeniedException("Bạn không có quyền cập nhật đơn hàng.");
        return user;
    }
}
