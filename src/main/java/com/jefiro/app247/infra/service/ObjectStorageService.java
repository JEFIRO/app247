package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.exception.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.UUID;

/**
 * Imagens no OCI Object Storage pela API compatível com S3. O banco guarda a URL pública
 * completa, que os apps usam direto em {@code Image.network}. O bucket precisa ter
 * visibilidade pública (leitura de objetos); a chave é aleatória, então não é adivinhável.
 */
@Service
public class ObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);
    private static final int MAX_DIMENSION = 8000;

    private final String bucket;
    private final String publicBaseUrl;
    private final long maxImageBytes;
    private final S3Client client;

    @Autowired
    public ObjectStorageService(
            @Value("${oci.s3.namespace:}") String namespace,
            @Value("${oci.s3.region:}") String region,
            @Value("${oci.s3.bucket:}") String bucket,
            @Value("${oci.s3.access-key:}") String accessKey,
            @Value("${oci.s3.secret-key:}") String secretKey,
            @Value("${app.storage.image.max-size:5MB}") DataSize maxImageSize) {
        this(namespace, region, bucket, maxImageSize,
                isBlank(namespace, region, bucket, accessKey, secretKey) ? null : S3Client.builder()
                        .region(Region.of(region))
                        .endpointOverride(URI.create(
                                "https://" + namespace + ".compat.objectstorage." + region + ".oraclecloud.com"))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                        // OCI só aceita path-style e não suporta o envio chunked assinado do SDK.
                        .serviceConfiguration(S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .chunkedEncodingEnabled(false)
                                .build())
                        .build());
    }

    ObjectStorageService(String namespace, String region, String bucket, DataSize maxImageSize, S3Client client) {
        this.bucket = bucket;
        this.maxImageBytes = maxImageSize.toBytes();
        this.client = client;
        this.publicBaseUrl = client == null ? null
                : "https://objectstorage." + region + ".oraclecloud.com/n/" + namespace + "/b/" + bucket + "/o/";
        if (client == null) log.warn("[OBJECT-STORAGE] OCI não configurado: uploads de imagem ficarão indisponíveis");
    }

    private static boolean isBlank(String... values) {
        for (String value : values) if (value == null || value.isBlank()) return true;
        return false;
    }

    /**
     * Valida e envia uma imagem JPEG/PNG. Se a transação atual for revertida, o objeto
     * enviado é apagado para não deixar lixo no bucket.
     *
     * @param folder prefixo lógico, ex. {@code produtos/{empresaId}}
     * @return URL pública do objeto
     */
    public String uploadImage(MultipartFile file, String folder) {
        S3Client s3 = requireClient();
        if (file == null || file.isEmpty()) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "IMAGE_EMPTY", "Arquivo de imagem vazio");
        }
        if (file.getSize() > maxImageBytes) {
            throw new ApiBusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE",
                    "A imagem deve ter no máximo " + DataSize.ofBytes(maxImageBytes).toMegabytes() + " MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FileStorageException("Falha ao ler a imagem enviada", e);
        }
        ImageFormat format = detect(bytes);
        String key = folder + "/" + UUID.randomUUID() + format.extension;
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket).key(key)
                            .contentType(format.contentType)
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new FileStorageException("Falha ao enviar a imagem para o Object Storage", e);
        }
        String url = publicBaseUrl + key;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) deleteQuietly(url);
                }
            });
        }
        return url;
    }

    /**
     * Apaga a imagem substituída só depois do commit: se a transação falhar, o registro
     * continua apontando para a imagem antiga e ela precisa existir.
     */
    public void deleteAfterCommit(String url) {
        if (!isManaged(url)) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(url);
                }
            });
        } else {
            deleteQuietly(url);
        }
    }

    /** Só objetos deste bucket são apagados; nomes antigos do disco local e URLs externas são ignorados. */
    boolean isManaged(String url) {
        return url != null && publicBaseUrl != null && url.startsWith(publicBaseUrl)
                && url.length() > publicBaseUrl.length();
    }

    private void deleteQuietly(String url) {
        if (!isManaged(url)) return;
        String key = url.substring(publicBaseUrl.length());
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("[OBJECT-STORAGE] não foi possível apagar key={}", key, e);
        }
    }

    private S3Client requireClient() {
        if (client == null) {
            throw new ApiBusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED",
                    "Armazenamento de imagens não configurado");
        }
        return client;
    }

    /** Lê só o cabeçalho da imagem: formato pelo conteúdo e dimensões sem decodificar os pixels. */
    static ImageFormat detect(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                ImageFormat format = ImageFormat.from(reader.getFormatName());
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new ApiBusinessException(HttpStatus.BAD_REQUEST, "IMAGE_DIMENSIONS_INVALID",
                            "A imagem deve ter no máximo " + MAX_DIMENSION + " x " + MAX_DIMENSION + " pixels");
                }
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalidImage();
        }
    }

    private static ApiBusinessException invalidImage() {
        return new ApiBusinessException(HttpStatus.BAD_REQUEST, "IMAGE_INVALID",
                "Envie uma imagem JPEG ou PNG válida");
    }

    enum ImageFormat {
        JPEG(".jpg", "image/jpeg"), PNG(".png", "image/png");

        final String extension;
        final String contentType;

        ImageFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        static ImageFormat from(String formatName) {
            String name = formatName == null ? "" : formatName.toLowerCase();
            if (name.equals("jpeg") || name.equals("jpg")) return JPEG;
            if (name.equals("png")) return PNG;
            throw invalidImage();
        }
    }
}
