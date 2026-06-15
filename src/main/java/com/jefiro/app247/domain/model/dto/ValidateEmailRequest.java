package com.jefiro.app247.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidateEmailRequest {
    @NotBlank
    @NotNull
    String email;
    @NotBlank
    @NotNull
    String nome;
    String code;
    
}
