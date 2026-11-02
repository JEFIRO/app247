package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.appclient.AppClienteContracts.*;
import com.jefiro.app247.domain.model.dto.comprovante.ComprovanteDigitalResponse;
import com.jefiro.app247.domain.model.enum_type.ProdutoCategoria;
import com.jefiro.app247.infra.service.AppClienteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/app-cliente")
public class AppClienteController {
    @Autowired
    private AppClienteService service;

    @GetMapping("/bootstrap")
    public Bootstrap bootstrap() {
        return service.bootstrap();
    }

    @GetMapping("/catalogo")
    public PageResponse<CatalogItem> catalogo(@RequestParam(required = false) String busca, @RequestParam(required = false) ProdutoCategoria categoria, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return service.catalog(busca, categoria, page, size);
    }

    @GetMapping("/compras")
    public PageResponse<PurchaseSummary> compras(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        validatePage(page, size);
        return service.purchases(page, size);
    }

    @GetMapping("/compras/{id}")
    public PurchaseDetail compra(@PathVariable String id) {
        return service.purchase(id);
    }

    @GetMapping(value = "/compras/{id}/comprovante", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> comprovante(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(service.receipt(id));
    }

    @GetMapping("/compras/{id}/comprovante/dados")
    public ResponseEntity<ComprovanteDigitalResponse> comprovanteDados(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.receiptData(id));
    }

    @PostMapping("/compras/{id}/comprovante/envios")
    public ResponseEntity<Accepted> enviar(@PathVariable String id, @Valid @RequestBody ReceiptSendRequest request) {
        return ResponseEntity.accepted().body(service.sendReceipt(id, request));
    }

    @PostMapping("/identificacao-tokens")
    public ResponseEntity<CustomerIdentificationTokenResponse> identificar() {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.identificationToken());
    }

    @GetMapping("/fidelidade")
    public Loyalty fidelidade() {
        return service.loyalty();
    }

    @GetMapping("/recompensas")
    public List<RewardResponse> recompensas() {
        return service.rewardList();
    }

    @PostMapping("/recompensas/{id}/resgates")
    public ResponseEntity<RedemptionResponse> resgatar(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.redeem(id, key));
    }

    @GetMapping("/cupons")
    public List<CouponResponse> cupons() {
        return service.couponList();
    }

    @PostMapping("/cupons/{id}/tokens")
    public ResponseEntity<TokenResponse> tokenCupom(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.couponToken(id));
    }

    @GetMapping("/preferencias")
    public PreferenceResponse preferencias() {
        return service.getPreferences();
    }

    @PutMapping("/preferencias")
    public PreferenceResponse preferencias(@Valid @RequestBody PreferenceRequest request) {
        return service.putPreferences(request);
    }

    @PostMapping("/compras/{id}/suporte")
    public ResponseEntity<SupportResponse> suporte(@PathVariable String id, @Valid @RequestBody SupportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.support(id, request));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Paginação inválida; size deve estar entre 1 e 100");
    }
}
