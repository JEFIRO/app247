package com.jefiro.app247.domain.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductDTO(

        @Size(max = 80, message = "O código deve ter no máximo 80 caracteres.")
        String codigo,

        @NotBlank(message = "O nome do produto não pode ficar vazio.")
        @Size(max = 180, message = "O nome do produto deve ter no máximo 180 caracteres.")
        String nome,

        @NotNull(message = "O preço deve ser informado.")
        @Positive(message = "O preço deve ser maior que zero.")
        @Digits(integer = 9, fraction = 6, message = "Preço fora do limite permitido.")
        BigDecimal preco,

        @Deprecated
        Integer quantidade,

        @NotBlank(message = "Informe a unidade de medida (ex: kg, g, un).")
        String unidadeMedida,

        @NotBlank(message = "A categoria do produto deve ser informada.")
        String categoria,

        @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres.")
        String descricao,

        // Exibida nos apps: bloqueia esquemas executáveis (javascript:, data:, vbscript:).
        @Size(max = 500, message = "A URL da foto deve ter no máximo 500 caracteres.")
        @Pattern(regexp = "(?i)^(?!\\s*(javascript|data|vbscript):).*$", message = "URL da foto inválida.")
        String foto,

        @Positive(message = "O peso deve ser maior que zero.")
        @Digits(integer = 12, fraction = 3, message = "Peso fora do limite permitido.")
        BigDecimal peso,

        @PositiveOrZero(message = "A tolerância de peso não pode ser negativa.")
        @Digits(integer = 12, fraction = 3, message = "Tolerância de peso fora do limite permitido.")
        BigDecimal pesoTolerancia,

        @Size(max = 80, message = "O código interno deve ter no máximo 80 caracteres.")
        String codigoInterno,

        List<@Valid CodigoBarrasRequest> codigosBarras,

        ProdutoFiscalRequest fiscal,

        Boolean ativo

) {
    public CreateProductDTO(String codigo, String nome, BigDecimal preco, Integer quantidade,
                            String unidadeMedida, String categoria, String descricao, String foto,
                            BigDecimal peso, BigDecimal pesoTolerancia) {
        this(codigo, nome, preco, quantidade, unidadeMedida, categoria, descricao, foto,
                peso, pesoTolerancia, codigo,
                List.of(new CodigoBarrasRequest(codigo, "INTERNO", true)), null, true);
    }

    public String codigoInternoEfetivo() {
        return codigoInterno == null || codigoInterno.isBlank() ? codigo : codigoInterno;
    }

    public List<CodigoBarrasRequest> codigosBarrasEfetivos() {
        if (codigosBarras != null && !codigosBarras.isEmpty()) return codigosBarras;
        return codigo == null || codigo.isBlank() ? List.of()
                : List.of(new CodigoBarrasRequest(codigo, "INTERNO", true));
    }

    public record CodigoBarrasRequest(
            @NotBlank(message = "Informe o código de barras") String codigo,
            String tipo,
            boolean principal,
            Boolean ativo
    ) {
        public CodigoBarrasRequest(String codigo, String tipo, boolean principal) {
            this(codigo, tipo, principal, true);
        }

        public boolean ativoEfetivo() {
            return ativo == null || ativo;
        }
    }

    public record ProdutoFiscalRequest(
            @Pattern(regexp = "\\d{8}", message = "NCM deve conter 8 dígitos") String ncm,
            @Pattern(regexp = "\\d{7}", message = "CEST deve conter 7 dígitos") String cest,
            @Size(max = 2) String origemMercadoria,
            @Size(max = 6) String unidadeTributavel,
            @DecimalMin(value = "0", inclusive = false) BigDecimal fatorConversaoTributavel,
            @Size(max = 14) String gtinTributavel,
            String perfilTributarioId
    ) {}
}
