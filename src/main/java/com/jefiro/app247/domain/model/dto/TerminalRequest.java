package com.jefiro.app247.domain.model.dto;

public record TerminalRequest(
        String nome,
        String serialNumber,
        String macAddress,
        String ipAddress)
{ }
