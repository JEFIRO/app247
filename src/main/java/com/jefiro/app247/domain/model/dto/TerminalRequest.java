package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code nome} também vira {@code codigo} (coluna de 80), por isso o limite é 80. */
public record TerminalRequest(
        @NotBlank(message = "Nome do terminal é obrigatório")
        @Size(max = 80, message = "Nome do terminal deve ter no máximo 80 caracteres")
        String nome,
        @Size(max = 120, message = "Número de série deve ter no máximo 120 caracteres")
        String serialNumber,
        @Size(max = 30, message = "MAC deve ter no máximo 30 caracteres")
        String macAddress,
        @Size(max = 45, message = "IP deve ter no máximo 45 caracteres")
        String ipAddress)
{ }
