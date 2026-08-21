package com.jefiro.app247.infra.exception;

public class ExternalServiceException extends RuntimeException {
    private final String service;
    private final ExternalFailureType failureType;

    public ExternalServiceException(String service, String message, Throwable cause) {
        this(service, ExternalFailureType.UNKNOWN, message, cause);
    }

    public ExternalServiceException(String service, ExternalFailureType failureType,
                                    String message, Throwable cause) {
        super(message, cause);
        this.service = service;
        this.failureType = failureType;
    }

    public String getService() {
        return service;
    }

    public ExternalFailureType getFailureType() {
        return failureType;
    }
}
