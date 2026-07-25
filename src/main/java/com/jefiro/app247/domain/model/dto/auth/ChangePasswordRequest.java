package com.jefiro.app247.domain.model.dto.auth;

public record ChangePasswordRequest(
        String userId,
        String oldPassword,
        String newPassword
) {}