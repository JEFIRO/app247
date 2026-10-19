package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ObjectStorageServiceTest {
    private static final String BASE = "https://objectstorage.sa-saopaulo-1.oraclecloud.com/n/ns/b/app-imagens/o/";
    private final S3Client s3 = mock(S3Client.class);
    private final ObjectStorageService service =
            new ObjectStorageService("ns", "sa-saopaulo-1", "app-imagens", DataSize.ofMegabytes(5), s3);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void enviaJpegComChaveAleatoriaETipoCorreto() throws Exception {
        String url = service.uploadImage(file(image("jpg")), "produtos/empresa-1");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("app-imagens");
        assertThat(request.getValue().key()).matches("produtos/empresa-1/[0-9a-f-]{36}\\.jpg");
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(url).isEqualTo(BASE + request.getValue().key());
    }

    @Test
    void enviaPngComExtensaoPeloConteudoENaoPeloNome() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "foto.jpg", "image/jpeg", image("png"));

        String url = service.uploadImage(png, "usuarios/u-1");

        assertThat(url).endsWith(".png");
    }

    @Test
    void recusaArquivoQueNaoEhImagem() {
        MockMultipartFile texto = new MockMultipartFile("file", "x.jpg", "image/jpeg",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadImage(texto, "produtos/e"))
                .isInstanceOf(ApiBusinessException.class)
                .extracting(e -> ((ApiBusinessException) e).getCode()).isEqualTo("IMAGE_INVALID");
        verifyNoInteractions(s3);
    }

    @Test
    void recusaFormatoForaDeJpegEPng() throws Exception {
        assertThatThrownBy(() -> service.uploadImage(file(image("gif")), "produtos/e"))
                .extracting(e -> ((ApiBusinessException) e).getCode()).isEqualTo("IMAGE_INVALID");
    }

    @Test
    void recusaDimensoesGigantesSemDecodificarPixels() {
        // Cabeçalho PNG de 20000x20000: decodificar exigiria ~1,6 GB de memória.
        assertThatThrownBy(() -> service.uploadImage(file(pngHeader(20_000, 20_000)), "produtos/e"))
                .extracting(e -> ((ApiBusinessException) e).getCode()).isEqualTo("IMAGE_DIMENSIONS_INVALID");
        verifyNoInteractions(s3);
    }

    @Test
    void recusaImagemAcimaDoLimite() throws Exception {
        ObjectStorageService pequeno = new ObjectStorageService("ns", "sa-saopaulo-1", "app-imagens",
                DataSize.ofBytes(100), s3);

        assertThatThrownBy(() -> pequeno.uploadImage(file(image("jpg")), "produtos/e"))
                .extracting(e -> ((ApiBusinessException) e).getCode()).isEqualTo("IMAGE_TOO_LARGE");
    }

    @Test
    void semConfiguracaoResponde503() throws Exception {
        ObjectStorageService semOci = new ObjectStorageService("", "", "", DataSize.ofMegabytes(5), null);

        assertThatThrownBy(() -> semOci.uploadImage(file(image("jpg")), "produtos/e"))
                .extracting(e -> ((ApiBusinessException) e).getCode()).isEqualTo("STORAGE_NOT_CONFIGURED");
    }

    @Test
    void rollbackApagaOObjetoEnviado() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        String url = service.uploadImage(file(image("jpg")), "produtos/e");

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(delete.capture());
        assertThat(BASE + delete.getValue().key()).isEqualTo(url);
    }

    @Test
    void commitMantemOObjetoEnviado() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        service.uploadImage(file(image("jpg")), "produtos/e");

        complete(TransactionSynchronization.STATUS_COMMITTED);

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void imagemSubstituidaSoEhApagadaAposCommitESoSeForDoBucket() {
        TransactionSynchronizationManager.initSynchronization();
        service.deleteAfterCommit(BASE + "produtos/e/antiga.jpg");
        service.deleteAfterCommit("abc_123.jpg");
        service.deleteAfterCommit("https://outro-site.example/foto.jpg");
        verifyNoInteractions(s3);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3, times(1)).deleteObject(delete.capture());
        assertThat(delete.getValue().key()).isEqualTo("produtos/e/antiga.jpg");
    }

    private static void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "foto", "application/octet-stream", bytes);
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }

    /** Assinatura PNG + chunk IHDR válido (com CRC), sem dados de imagem. */
    private static byte[] pngHeader(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
        png.put(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
                .putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
        return png.array();
    }
}
