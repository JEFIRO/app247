package com.jefiro.app247.infra.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.CheckoutSession;
import com.jefiro.app247.domain.model.dto.response.CarrinhoResponseDTO;
import com.jefiro.app247.infra.repository.CheckoutSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class CheckoutSessionService {

    private final CheckoutSessionRepository repository;
    @Autowired
    private CarrinhoService service;

    public CheckoutSessionService(CheckoutSessionRepository repository) {
        this.repository = repository;
    }

    public CheckoutSession create(Carrinho carrinho) {

        CheckoutSession session = new CheckoutSession(carrinho);

        repository.save(session);

        return session;
    }

    public CarrinhoResponseDTO getCarrinho(String sessionRequest) {
        CheckoutSession session = repository.findById(sessionRequest).orElseThrow(() -> new RuntimeException("sessao expirada"));
        Carrinho carrinho = service.getById(session.getCartId());
        
        return new CarrinhoResponseDTO(carrinho);
    }

    public byte[] gerarQRCode(String id) throws Exception {

        CheckoutSession session = repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Sessão inválida"));

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode("app247://session/" + session.getSessionId(), BarcodeFormat.QR_CODE, 250, 250);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

        return pngOutputStream.toByteArray();
    }

    public boolean setUser(String id, String session) {
        try {
            CheckoutSession session1 = repository.findById(session).get();
            session1.setUserId(id);
            repository.save(session1);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}