package com.jefiro.app247.apresentacao.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.Produto;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.domain.model.enum_type.UnidadeMedida;
import com.jefiro.app247.infra.repository.EstoqueEmpresaRepository;
import com.jefiro.app247.infra.repository.ProdutoCodigoBarrasRepository;
import com.jefiro.app247.infra.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entrada de mercadoria pela NF-e do fornecedor ({@code /estoque-empresa/notas-fiscais}):
 * o XML é lido, os itens são casados com o catálogo, o administrador revisa e, ao confirmar,
 * os produtos novos são criados e o estoque central recebe a ENTRADA.
 */
class NotaFiscalEntradaIntegrationTest extends IntegracaoBase {
    private static final String CHAVE = "29261111222333000181550010000012341123456783";
    private static final String BASE = "/estoque-empresa/notas-fiscais";

    @Autowired ProdutoRepository produtos;
    @Autowired ProdutoCodigoBarrasRepository barcodes;
    @Autowired EstoqueEmpresaRepository estoques;
    private User admin;
    private Produto agua;

    @BeforeEach
    void cenario() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        // CNPJ com máscara: a comparação com o destinatário da nota usa só os dígitos.
        Empresa empresa = empresas.saveAndFlush(Empresa.builder().razaoSocial("Mundo " + sufixo)
                .nomeFantasia("Mundo " + sufixo).cnpj("24.700.000/0001-62")
                .email(sufixo + "@empresa.test").ativo(true).build());
        admin = novoUsuario(empresa, "39053344705", RoleUser.ADMIN);
        agua = new Produto();
        agua.setEmpresa(empresa);
        agua.setCodigoInterno("AGUA-500");
        agua.setNome("Água 500ml");
        agua.setPreco(new BigDecimal("3.00"));
        agua.setCategoria(ProdutoCategoria.BEBIDA);
        agua.setUnidadeMedida(UnidadeMedida.UN);
        agua.adicionarCodigoBarras("7890000000024", "EAN", true);
        agua = produtos.saveAndFlush(agua);
    }

    @Test
    @DisplayName("Lê o XML e casa o EAN já cadastrado; itens novos ficam pendentes de preço")
    void leXml() throws Exception {
        enviarXml(xml())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chave").value(CHAVE))
                .andExpect(jsonPath("$.emitenteNome").value("DISTRIBUIDORA EXEMPLO LTDA"))
                .andExpect(jsonPath("$.totalItens").value(4))
                .andExpect(jsonPath("$.podeConfirmar").value(false))
                .andExpect(jsonPath("$.itens[0].acao").value("CRIAR"))
                .andExpect(jsonPath("$.itens[0].categoria").value("BEBIDA"))
                .andExpect(jsonPath("$.itens[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$.itens[1].acao").value("VINCULAR"))
                .andExpect(jsonPath("$.itens[1].produtoId").value(agua.getIdProduto()))
                .andExpect(jsonPath("$.itens[1].status").value("PRONTO"))
                .andExpect(jsonPath("$.itens[3].ean").doesNotExist());
    }

    @Test
    @DisplayName("Confirma: cria os produtos novos, converte caixa em unidades e lança a entrada")
    void confirma() throws Exception {
        JsonNode nota = corpo(enviarXml(xml()));
        String id = nota.get("id").asText();
        JsonNode itens = nota.get("itens");
        // Refrigerante vem em caixa com 12 latas.
        ajustar(id, itens.get(0).get("id").asText(), "{\"precoVenda\":5.50,\"fatorConversao\":12}")
                .andExpect(jsonPath("$.itens[0].status").value("PRONTO"))
                .andExpect(jsonPath("$.itens[0].quantidadeEntrada").value(24));
        ajustar(id, itens.get(2).get("id").asText(), "{\"precoVenda\":3.99}");
        ajustar(id, itens.get(3).get("id").asText(), "{\"precoVenda\":15.90,\"categoria\":\"congelados\"}")
                .andExpect(jsonPath("$.podeConfirmar").value(true));

        mvc.perform(post(BASE + "/" + id + "/confirmar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.produtosCriados").value(3))
                .andExpect(jsonPath("$.entradasLancadas").value(4));

        String empresaId = admin.getEmpresa().getId();
        assertThat(estoques.findByEmpresaIdAndProdutoIdProduto(empresaId, agua.getIdProduto()).orElseThrow()
                .getQuantidade()).isEqualByComparingTo("24");
        Produto refri = barcodes.findByEmpresaIdAndCodigoBarrasAndAtivoTrue(empresaId, "7890000000017")
                .orElseThrow().getProduto();
        assertThat(refri.getPreco()).isEqualByComparingTo("5.50");
        assertThat(estoques.findByEmpresaIdAndProdutoIdProduto(empresaId, refri.getIdProduto()).orElseThrow()
                .getQuantidade()).isEqualByComparingTo("24");
        Produto paoDeQueijo = produtos.findByCodigoInternoAndEmpresaId("NF11222333-PQJ1KG", empresaId).orElseThrow();
        assertThat(paoDeQueijo.getCategoria()).isEqualTo(ProdutoCategoria.CONGELADOS);
    }

    @Test
    @DisplayName("Não confirma com item pendente e não importa a mesma NF-e duas vezes")
    void bloqueios() throws Exception {
        JsonNode nota = corpo(enviarXml(xml()));
        String id = nota.get("id").asText();
        mvc.perform(post(BASE + "/" + id + "/confirmar").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());

        for (int i : new int[]{0, 2, 3}) {
            ajustar(id, nota.get("itens").get(i).get("id").asText(), "{\"precoVenda\":4.00}");
        }
        mvc.perform(post(BASE + "/" + id + "/confirmar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        enviarXml(xml()).andExpect(status().isConflict());
        mvc.perform(get(BASE + "/chave/" + CHAVE).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.notaId").value(id))
                .andExpect(jsonPath("$.sefazDisponivel").value(false));
    }

    @Test
    @DisplayName("Recusa NF-e emitida para outro CNPJ")
    void recusaOutroDestinatario() throws Exception {
        String outro = new String(xml()).replace("<CNPJ>24700000000162</CNPJ>", "<CNPJ>99888777000166</CNPJ>");
        enviarXml(outro.getBytes())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Consulta pela chave sem certificado pede o XML")
    void chaveSemCertificado() throws Exception {
        mvc.perform(get(BASE + "/chave/" + CHAVE).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("1234"))
                .andExpect(jsonPath("$.notaId").doesNotExist())
                .andExpect(jsonPath("$.mensagem").value("Envie o XML desta NF-e para ver os itens"));
        mvc.perform(post(BASE + "/chave/" + CHAVE + "/importar").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    private byte[] xml() throws Exception {
        return getClass().getResourceAsStream("/nfe/nfe-exemplo.xml").readAllBytes();
    }

    private ResultActions enviarXml(byte[] xml) throws Exception {
        return mvc.perform(multipart(BASE + "/xml")
                .file(new MockMultipartFile("file", "nfe-1234.xml", "application/xml", xml))
                .header("Authorization", bearer(admin)));
    }

    private ResultActions ajustar(String notaId, String itemId, String body) throws Exception {
        return mvc.perform(put(BASE + "/" + notaId + "/itens/" + itemId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
