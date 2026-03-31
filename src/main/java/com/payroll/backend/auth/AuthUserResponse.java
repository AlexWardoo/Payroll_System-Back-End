package com.payroll.backend.auth;

import com.payroll.backend.user.User;

public record AuthUserResponse(
        Long id,
        String username,
        String displayName,
        String role,
        boolean canViewProfit
) {
    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                Boolean.TRUE.equals(user.getCanViewProfit())
        );
    }
}
