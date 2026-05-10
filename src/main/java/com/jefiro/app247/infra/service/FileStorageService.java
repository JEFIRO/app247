package com.jefiro.app247.infra.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final String uploadDir = "uploads/";

    public String salvarArquivo(MultipartFile file) throws IOException {

        String nomeArquivo = UUID.randomUUID() + "_" + file.getOriginalFilename();

        Path path = Paths.get(uploadDir + nomeArquivo);

        Files.createDirectories(path.getParent());

        Files.copy(file.getInputStream(), path);

        return nomeArquivo;
    }
}