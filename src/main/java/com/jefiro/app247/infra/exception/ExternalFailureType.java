package com.jefiro.app247.infra.exception;

public enum ExternalFailureType {
    AUTHENTICATION,
    TERMINAL_NOT_FOUND,
    ACTIVE_CHARGE,
    IDEMPOTENCY_CONFLICT,
    INVALID_REQUEST,
    TIMEOUT,
    UNAVAILABLE,
    UNKNOWN
}
