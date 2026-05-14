package com.jefiro.app247.infra.controller;

import com.jefiro.app247.infra.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/files")
public class FileController {
    @Autowired
    private FileStorageService service;




    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam MultipartFile file) throws IOException {

        String nomeArquivo = service.salvarArquivo(file);

        String url = "http://localhost:8080/files/" + nomeArquivo;

        return ResponseEntity.ok(url);
    }
}
