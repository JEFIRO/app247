package com.jefiro.app247.domain.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductDTO(

        String codigo,

        @NotBlank(message = "O nome do produto não pode ficar vazio.")
        String nome,

        @NotNull(message = "O preço deve ser informado.")
        @Positive(message = "O preço deve ser maior que zero.")
        BigDecimal preco,

        @Deprecated
        Integer quantidade,

        @NotBlank(message = "Informe a unidade de medida (ex: kg, g, un).")
        String unidadeMedida,

        @NotBlank(message = "A categoria do produto deve ser informada.")
        String categoria,

        String descricao,

        String foto,

        @Positive(message = "O peso deve ser maior que zero.")
        BigDecimal peso,

        @PositiveOrZero(message = "A tolerância de peso não pode ser negativa.")
        BigDecimal pesoTolerancia,

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
