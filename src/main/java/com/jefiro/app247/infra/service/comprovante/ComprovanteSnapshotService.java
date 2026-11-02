package com.jefiro.app247.infra.service.comprovante;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComprovanteSnapshotService {
    private final ComprovanteDigitalService digitalService;

    @Autowired
    public ComprovanteSnapshotService(ComprovanteDigitalService digitalService) {
        this.digitalService = digitalService;
    }

    @Transactional(readOnly = true)
    public ComprovanteCompraRequest montar(String orderId, String terminalId) {
        return ComprovanteCompraRequest.de(digitalService.paraTerminal(orderId, terminalId));
    }
}
