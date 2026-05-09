package com.jefiro.app247.infra.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.dto.CheckoutSession;
import com.jefiro.app247.infra.repository.CheckoutSessionRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class CheckoutSessionService {

    private final CheckoutSessionRepository repository;

    public CheckoutSessionService(CheckoutSessionRepository repository) {
        this.repository = repository;
    }

    public CheckoutSession create(Carrinho carrinho) {

        CheckoutSession session = new CheckoutSession(carrinho);

        repository.save(session);

        return session;
    }

    public byte[] gerarQRCode(String session) throws Exception {

        if (repository.findById(session).getSessionId().isEmpty()) {
            throw new Exception("session invalida");
        }

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode("app24por7://session/" + session, BarcodeFormat.QR_CODE, 250, 250);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

        return pngOutputStream.toByteArray();
    }

    public boolean setUser(String id, String session) {
        try {
            CheckoutSession session1 = repository.findById(session);
            session1.setUserId(id);
            repository.save(session1);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}