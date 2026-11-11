package com.jefiro.app247.apresentacao.unitario;

import com.jefiro.app247.infra.service.ChaveAcessoNfe;
import com.jefiro.app247.infra.service.NfeXmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Leitura do XML da NF-e e validação da chave de acesso impressa no DANFE. */
class NfeXmlParserUnitTest {
    private static final String CHAVE = "29261111222333000181550010000012341123456783";
    private final NfeXmlParser parser = new NfeXmlParser();

    @Test
    @DisplayName("Decompõe a chave de acesso e confere o dígito verificador")
    void chaveValida() {
        ChaveAcessoNfe chave = ChaveAcessoNfe.parse("2926 1111 2223 3300 0181 5500 1000 0012 3411 2345 6783");
        assertThat(chave.chave()).isEqualTo(CHAVE);
        assertThat(chave.uf()).isEqualTo("29");
        assertThat(chave.cnpjEmitente()).isEqualTo("11222333000181");
        assertThat(chave.serie()).isEqualTo("1");
        assertThat(chave.numero()).isEqualTo("1234");
    }

    @Test
    @DisplayName("Recusa chave com dígito verificador errado ou tamanho errado")
    void chaveInvalida() {
        assertThatThrownBy(() -> ChaveAcessoNfe.parse(CHAVE.substring(0, 43) + "0"))
                .hasMessageContaining("confira os dígitos");
        assertThatThrownBy(() -> ChaveAcessoNfe.parse("123")).hasMessageContaining("44 dígitos");
    }

    @Test
    @DisplayName("Lê emitente, destinatário e itens; SEM GTIN vira item sem EAN")
    void leXml() throws Exception {
        byte[] xml = getClass().getResourceAsStream("/nfe/nfe-exemplo.xml").readAllBytes();
        NfeXmlParser.Nota nota = parser.ler(xml);
        assertThat(nota.chave().chave()).isEqualTo(CHAVE);
        assertThat(nota.numero()).isEqualTo("1234");
        assertThat(nota.emitenteCnpj()).isEqualTo("11222333000181");
        assertThat(nota.destinatarioDocumento()).isEqualTo("24700000000162");
        assertThat(nota.valorTotal()).isEqualByComparingTo("248.80");
        assertThat(nota.itens()).hasSize(4);
        assertThat(nota.itens().get(0).unidade()).isEqualTo("CX");
        assertThat(nota.itens().get(0).quantidade()).isEqualByComparingTo("2");
        assertThat(nota.itens().get(3).ean()).isNull();
    }

    @Test
    @DisplayName("Recusa XML com DTD (proteção contra XXE)")
    void recusaDtd() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>
                <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">&e;</nfeProc>
                """;
        assertThatThrownBy(() -> parser.ler(xxe.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("Não foi possível ler o XML");
    }

    @Test
    @DisplayName("Recusa nota com protocolo de rejeição")
    void recusaNaoAutorizada() throws Exception {
        String xml = new String(getClass().getResourceAsStream("/nfe/nfe-exemplo.xml").readAllBytes(),
                StandardCharsets.UTF_8).replace("<cStat>100</cStat>", "<cStat>302</cStat>");
        assertThatThrownBy(() -> parser.ler(xml.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("não está autorizada");
    }
}
