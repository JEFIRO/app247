package com.jefiro.app247.infra.service.admin;

import com.jefiro.app247.domain.model.AuditLog;
import com.jefiro.app247.domain.model.dto.admin.AdminActivityResponse;
import com.jefiro.app247.infra.repository.AuditLogRepository;
import com.jefiro.app247.infra.service.EmpresaContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AdminActivityQueryService {
    private static final Map<String, ActivityTemplate> TEMPLATES = Map.ofEntries(
            Map.entry("PRODUCT_CREATED", new ActivityTemplate("PRODUCT", "Produto cadastrado", "/produtos")),
            Map.entry("PRODUCT_UPDATED", new ActivityTemplate("PRODUCT", "Produto atualizado", "/produtos")),
            Map.entry("PRODUCT_ACTIVATED", new ActivityTemplate("PRODUCT", "Produto ativado", "/produtos")),
            Map.entry("PRODUCT_DEACTIVATED", new ActivityTemplate("PRODUCT", "Produto desativado", "/produtos")),
            Map.entry("PROMOTION_CREATED", new ActivityTemplate("PROMOTION", "Promoção criada", "/promocoes")),
            Map.entry("PROMOTION_UPDATED", new ActivityTemplate("PROMOTION", "Promoção atualizada", "/promocoes")),
            Map.entry("TRANSFERENCIA_CRIADA", new ActivityTemplate("STOCK", "Transferência criada", "/estoque")),
            Map.entry("TRANSFERENCIA_CONFIRMADA", new ActivityTemplate("STOCK", "Transferência concluída", "/estoque")),
            Map.entry("TRANSFERENCIA_CANCELADA", new ActivityTemplate("STOCK", "Transferência cancelada", "/estoque")),
            Map.entry("INVENTARIO_FINALIZADO", new ActivityTemplate("STOCK", "Inventário finalizado", "/estoque")),
            Map.entry("IMPORTACAO_PRODUTO_CONCLUIDA", new ActivityTemplate("PRODUCT", "Importação de produtos concluída", "/produtos")),
            Map.entry("ESTOQUE_EMPRESA_ENTRADA", new ActivityTemplate("STOCK", "Entrada no estoque central", "/estoque")),
            Map.entry("STOCK_ENTRY", new ActivityTemplate("STOCK", "Entrada de estoque registrada", "/estoque")),
            Map.entry("STOCK_ADJUSTED", new ActivityTemplate("STOCK", "Estoque ajustado", "/estoque"))
    );

    @Autowired private AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<AdminActivityResponse> recent(int requestedSize) {
        int size = Math.min(Math.max(requestedSize, 1), 30);
        return auditLogRepository.findAllByEmpresaIdAndActionInOrderByCreatedAtDesc(
                        EmpresaContext.require(), TEMPLATES.keySet(), PageRequest.of(0, size))
                .stream()
                .map(this::map)
                .toList();
    }

    private AdminActivityResponse map(AuditLog log) {
        ActivityTemplate template = TEMPLATES.get(log.getAction());
        return new AdminActivityResponse(
                log.getId(), template.type(), template.title(),
                detail(log), template.destination(), log.getCreatedAt());
    }

    private String detail(AuditLog log) {
        if (log.getEntityId() == null || log.getEntityId().isBlank()) return null;
        return log.getEntityType() + " · " + compact(log.getEntityId());
    }

    private String compact(String value) {
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private record ActivityTemplate(String type, String title, String destination) {
    }
}
