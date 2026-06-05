package com.jefiro.app247.infra.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileStorageService {

    private final String uploadDir = "uploads/";

    public String salvarArquivo(MultipartFile file) throws IOException {

    if (file == null || file.isEmpty()) {
        throw new RuntimeException("Arquivo vazio");
    }

    String originalNome = file.getOriginalFilename();

    if (originalNome == null) {
        throw new RuntimeException("Nome do arquivo inválido");
    }

    String extensao = originalNome.substring(originalNome.lastIndexOf(".")).toLowerCase();

    if (!extensao.equals(".jpg") && !extensao.equals(".jpeg")) {
        throw new RuntimeException("Apenas arquivos JPG e JPEG são permitidos");
    }


    long timestamp = System.currentTimeMillis();

    String hash;
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(file.getBytes());
        hash = HexFormat.of().formatHex(hashBytes).substring(0, 16);
    } catch (Exception e) {
        throw new RuntimeException("Erro ao gerar hash");
    }

    String nomeArquivo = hash + "_" + timestamp + extensao;

    Path path = Paths.get(uploadDir, nomeArquivo);

    Files.createDirectories(path.getParent());

    Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

    return nomeArquivo;
}
}