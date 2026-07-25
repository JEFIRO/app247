package com.jefiro.app247.domain.model.dto;

public record MercadoPagoTokenResponse(
        String access_token,
        String token_type,
        Long expires_in,
        String scope,
        String user_id,
        String refresh_token,
        String public_key,
        Boolean live_mode
) {
}
