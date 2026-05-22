package com.jefiro.app247.domain.model.dto.auth;

import com.jefiro.app247.domain.model.dto.UserResponseDTO;

public record AuthResponse(String token, UserResponseDTO user) {

}
