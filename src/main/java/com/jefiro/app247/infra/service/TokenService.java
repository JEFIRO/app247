package com.jefiro.app247.infra.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.jefiro.app247.domain.model.auth.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TokenService {
    @Value(value = "${api.secret.token}")
    private String senha;

    public String generateToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(senha);
            String token = JWT.create()
                    .withSubject(user.getCpf())
                    .withClaim("userId", user.getIdUser())
                    .withClaim("empresaId", user.getEmpresa().getId())
                    .withExpiresAt(genereteExpirationDate())
                    .sign(algorithm);
            return token;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String validate(String token) {
        return validateIdentity(token).subject();
    }

    public TokenIdentity validateIdentity(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(senha);
            DecodedJWT jwt = JWT.require(algorithm)
                    .build()
                    .verify(token);
            return new TokenIdentity(
                    jwt.getSubject(),
                    jwt.getClaim("userId").asString(),
                    jwt.getClaim("empresaId").asString()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Instant genereteExpirationDate() {
        return Instant.now().plus(java.time.Duration.ofHours(200));
    }

    public record TokenIdentity(String subject, String userId, String empresaId) {
    }
}
