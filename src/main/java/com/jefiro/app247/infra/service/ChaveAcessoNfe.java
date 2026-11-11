package com.jefiro.app247.infra.service;

/**
 * Chave de acesso da NF-e (44 dígitos), como impressa no DANFE:
 * cUF(2) AAMM(4) CNPJ(14) modelo(2) série(3) número(9) tpEmis(1) código(8) DV(1).
 */
public record ChaveAcessoNfe(String chave, String uf, String anoMes, String cnpjEmitente,
                             String modelo, String serie, String numero) {

    public static ChaveAcessoNfe parse(String valor) {
        String chave = valor == null ? "" : valor.replaceAll("\\D", "");
        if (chave.length() != 44) throw new IllegalArgumentException("A chave de acesso deve ter 44 dígitos");
        if (digitoVerificador(chave.substring(0, 43)) != chave.charAt(43) - '0') {
            throw new IllegalArgumentException("Chave de acesso inválida: confira os dígitos");
        }
        String modelo = chave.substring(20, 22);
        if (!modelo.equals("55") && !modelo.equals("65")) {
            throw new IllegalArgumentException("A chave não é de uma NF-e (modelo " + modelo + ")");
        }
        return new ChaveAcessoNfe(chave, chave.substring(0, 2), chave.substring(2, 6), chave.substring(6, 20),
                modelo, String.valueOf(Integer.parseInt(chave.substring(22, 25))),
                String.valueOf(Integer.parseInt(chave.substring(25, 34))));
    }

    /** Módulo 11 com pesos 2 a 9 da direita para a esquerda; resto 0 ou 1 vira DV 0. */
    static int digitoVerificador(String base43) {
        int soma = 0;
        int peso = 2;
        for (int i = base43.length() - 1; i >= 0; i--) {
            soma += (base43.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
