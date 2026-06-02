package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.TerminalActivationResponse;
import com.jefiro.app247.infra.service.TerminalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/terminal")
public class TerminalController {

    @Autowired
    TerminalService service;

    @GetMapping("/serial/{serial}")
    public ResponseEntity<TerminalActivationResponse> bySerial(@PathVariable String serial) {
        return ResponseEntity.ok(service.getBySerial(serial));
    }
}
