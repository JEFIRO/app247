package com.jefiro.app247.domain.model.dto.auth;

public record ChangePasswordRequest(
        Long userId,
        String oldPassword,
        String newPassword
) {}