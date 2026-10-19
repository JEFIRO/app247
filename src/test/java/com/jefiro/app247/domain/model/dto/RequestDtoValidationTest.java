package com.jefiro.app247.domain.model.dto;

import com.jefiro.app247.domain.model.dto.auth.AuthDTO;
import com.jefiro.app247.domain.model.dto.estoque.ContagemInventarioRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras de entrada que antes só falhavam no banco (409 enganoso) ou nem falhavam. */
class RequestDtoValidationTest {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final EnderecoDTO ENDERECO =
            new EnderecoDTO("Rua A", "10", "", "Centro", "Feira", "BA", "44000-000");

    @Test
    void condominioExigeNomeEAceitaCnpjVazioOuValido() {
        assertThat(invalid(new CondominioRequest(" ", null, null))).contains("nome");
        assertThat(invalid(new CondominioRequest("Res", "123", null))).contains("cnpj");
        assertThat(invalid(new CondominioRequest("Res", "", ENDERECO))).isEmpty();
        assertThat(invalid(new CondominioRequest("Res", "12.345.678/0001-90", ENDERECO))).isEmpty();
        assertThat(invalid(new CondominioRequest("Res", "12345678000190", null))).isEmpty();
    }

    @Test
    void enderecoInformadoPrecisaEstarCompleto() {
        CondominioRequest semRua = new CondominioRequest("Res", "",
                new EnderecoDTO(null, "10", null, "Centro", "Feira", "BA", "44000000"));
        CondominioRequest cepInvalido = new CondominioRequest("Res", "",
                new EnderecoDTO("Rua", "10", null, "Centro", "Feira", "Bahia", "4400"));

        assertThat(invalid(semRua)).contains("endereco.rua");
        assertThat(invalid(cepInvalido)).contains("endereco.estado", "endereco.cep");
    }

    @Test
    void terminalLimitaNomeAoTamanhoDoCodigo() {
        assertThat(invalid(new TerminalRequest("x".repeat(81), null, "", ""))).contains("nome");
        assertThat(invalid(new TerminalRequest("", null, null, null))).contains("nome");
        assertThat(invalid(new TerminalRequest("Terminal 1", "SN-1", "", ""))).isEmpty();
    }

    @Test
    void gestorDoOnboardingExigeSenhaECpf() {
        UserRequestDTO semSenha = new UserRequestDTO("Ana", "Souza", "ana@example.test", null, null,
                null, null, null);

        assertThat(invalid(semSenha)).contains("senha", "cpf");
        assertThat(invalid(new UserRequestDTO("Ana", "Souza", "ana@example.test", "x".repeat(73),
                "52998224725", null, null, null))).contains("senha");
    }

    @Test
    void produtoBloqueiaFotoExecutavelETextoLongo() {
        assertThat(invalid(produto("javascript:alert(1)", "Produto"))).contains("foto");
        assertThat(invalid(produto(" DATA:text/html,x", "Produto"))).contains("foto");
        assertThat(invalid(produto("https://cdn.example/foto.jpg", "x".repeat(181)))).contains("nome");
        assertThat(invalid(produto("/files/abc.jpg", "Produto"))).isEmpty();
        assertThat(invalid(produto(null, "Produto"))).isEmpty();
    }

    @Test
    void contagemDeInventarioNaoAceitaNegativoNemValorGigante() {
        assertThat(invalid(new ContagemInventarioRequest(new BigDecimal("-1"), null))).contains("quantidadeContada");
        assertThat(invalid(new ContagemInventarioRequest(new BigDecimal("1e15"), null))).contains("quantidadeContada");
        assertThat(invalid(new ContagemInventarioRequest(BigDecimal.ZERO, null))).isEmpty();
    }

    @Test
    void loginLimitaTamanho() {
        assertThat(invalid(new AuthDTO("1".repeat(21), "senha"))).contains("cpf");
        assertThat(invalid(new AuthDTO("52998224725", "x".repeat(101)))).contains("senha");
    }

    private static CreateProductDTO produto(String foto, String nome) {
        return new CreateProductDTO("COD-1", nome, new BigDecimal("10.50"), null, "UN", "BEBIDAS",
                null, foto, null, null);
    }

    private static Set<String> invalid(Object request) {
        Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
