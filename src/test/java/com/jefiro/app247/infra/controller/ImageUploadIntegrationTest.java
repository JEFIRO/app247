package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.Empresa;
import com.jefiro.app247.domain.model.auth.RoleUser;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.infra.repository.EmpresaRepository;
import com.jefiro.app247.infra.repository.UserRepository;
import com.jefiro.app247.infra.service.ObjectStorageService;
import com.jefiro.app247.infra.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload de imagem vai para o Object Storage; o servidor não serve mais arquivos locais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageUploadIntegrationTest {
    private static final String BASE = "https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/ns/b/app-imagens/o/";

    @Autowired MockMvc mvc;
    @Autowired EmpresaRepository empresas;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @MockitoBean ObjectStorageService storage;

    @Test
    void fotoDePerfilGuardaUrlDoBucketEApagaAAnterior() throws Exception {
        User user = user(BASE + "usuarios/antiga.jpg");
        String nova = BASE + "usuarios/" + user.getIdUser() + "/nova.jpg";
        when(storage.uploadImage(any(), eq("usuarios/" + user.getIdUser()))).thenReturn(nova);

        mvc.perform(multipart("/user/foto").file(new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1}))
                        .param("id", user.getIdUser())
                        .header("Authorization", "Bearer " + tokens.generateToken(user)))
                .andExpect(status().isOk());

        assertThat(users.findById(user.getIdUser()).orElseThrow().getFotoPerfil()).isEqualTo(nova);
        verify(storage).deleteAfterCommit(BASE + "usuarios/antiga.jpg");
    }

    @Test
    void rotasAntigasDeArquivoNaoExistemMais() throws Exception {
        mvc.perform(get("/files/qualquer.jpg")).andExpect(status().isUnauthorized());
        User user = user(null);
        mvc.perform(multipart("/files/upload").file(new MockMultipartFile("file", "f.jpg", "image/jpeg", new byte[]{1}))
                        .header("Authorization", "Bearer " + tokens.generateToken(user)))
                .andExpect(status().isForbidden());
    }

    private User user(String foto) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        Empresa empresa = new Empresa();
        empresa.setRazaoSocial("Empresa Img " + sufixo);
        empresa.setNomeFantasia("Empresa Img " + sufixo);
        empresa.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        empresa.setEmail("img-" + sufixo + "@example.test");
        empresa.setAtivo(true);
        empresa = empresas.saveAndFlush(empresa);
        String cpf = String.format("%011d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000_000L));
        return users.saveAndFlush(User.builder().empresa(empresa).nome("Pessoa")
                .email("img-" + sufixo + "@example.test").senha("hash").cpf(cpf).ativo(true)
                .role(RoleUser.ADMIN).fotoPerfil(foto).build());
    }
}
