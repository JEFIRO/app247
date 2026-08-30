package com.jefiro.app247.infra.controller;

import com.jefiro.app247.domain.model.dto.*;
import com.jefiro.app247.domain.model.enum_type.AbrangenciaPromocao;
import com.jefiro.app247.domain.model.enum_type.StatusPromocao;
import com.jefiro.app247.infra.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/promocoes")
public class PromocaoController {
    @Autowired private PromocaoService promocaoService;
    @Autowired private ProdutoService produtoService;
    @Autowired private CondominioService condominioService;
    @Autowired private PricingService pricingService;

    @GetMapping
    public List<PromocaoResponse> listar(
            @RequestParam(required = false) AbrangenciaPromocao abrangencia,
            @RequestParam(required = false) StatusPromocao status,
            @RequestParam(required = false) String condominioId,
            @RequestParam(required = false) String produtoId) {
        return promocaoService.listar(abrangencia, status, condominioId, produtoId);
    }

    @PostMapping
    public ResponseEntity<PromocaoResponse> criar(@RequestBody @Valid PromocaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promocaoService.criar(request));
    }

    @GetMapping("/{id}")
    public PromocaoResponse buscar(@PathVariable String id) {
        return promocaoService.buscar(id);
    }

    @PutMapping("/{id}")
    public PromocaoResponse atualizar(@PathVariable String id,
                                       @RequestBody @Valid PromocaoRequest request) {
        return promocaoService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public PromocaoResponse alterarStatus(@PathVariable String id,
                                           @RequestBody @Valid PromocaoStatusRequest request) {
        return promocaoService.alterarStatus(id, request.ativo());
    }

    @GetMapping("/preco")
    public PrecoProdutoResponse preco(@RequestParam String produtoId,
                                      @RequestParam String condominioId) {
        String empresaId = EmpresaContext.require();
        var produto = produtoService.buscarPorIdDoTenant(produtoId, empresaId);
        var condominio = condominioService.buscarDoTenant(condominioId, empresaId);
        return PrecoProdutoResponse.from(pricingService.calcular(
                produto, condominio, LocalDateTime.now(ZoneOffset.UTC)));
    }
}
