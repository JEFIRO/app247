package com.jefiro.app247.infra.controller.comprovante;

import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteRequest;
import com.jefiro.app247.infra.service.comprovante.ComprovanteEnvioResponse;
import com.jefiro.app247.infra.service.comprovante.ComprovanteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comprovante")
public class ComprovanteController {
    private final ComprovanteService service;

    @Autowired
    public ComprovanteController(ComprovanteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ComprovanteEnvioResponse> enviar(
            @Valid @RequestBody ComprovanteRequest request) {
        return ResponseEntity.ok(service.enviar(request));
    }
}
