package com.jefiro.app247.infra.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;

@Service
public class FileStorageService {

    private final String uploadDir = "uploads/";

    public String salvarArquivo(MultipartFile file) throws IOException {

    if (file == null || file.isEmpty()) {
        throw new IllegalArgumentException("Arquivo vazio");
    }

    String originalNome = file.getOriginalFilename();

    if (originalNome == null) {
        throw new IllegalArgumentException("Nome do arquivo inválido");
    }

    String extensao = originalNome.substring(originalNome.lastIndexOf(".")).toLowerCase();

    if (!extensao.equals(".jpg") && !extensao.equals(".jpeg")) {
        throw new IllegalArgumentException("Apenas arquivos JPG e JPEG são permitidos");
    }

    if (ImageIO.read(file.getInputStream()) == null) {
        throw new IllegalArgumentException("Conteúdo do arquivo não é uma imagem válida");
    }


    long timestamp = System.currentTimeMillis();

    String hash;
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(file.getBytes());
        hash = HexFormat.of().formatHex(hashBytes).substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 indisponível", e);
    }

    String nomeArquivo = hash + "_" + timestamp + extensao;

    Path path = Paths.get(uploadDir, nomeArquivo);

    Files.createDirectories(path.getParent());

    Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

    return nomeArquivo;
}
}
