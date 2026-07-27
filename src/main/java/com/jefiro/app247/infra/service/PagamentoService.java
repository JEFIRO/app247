package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.dto.mercadopago.OrderWebhookNotification;
import com.jefiro.app247.domain.model.enum_type.PagamentoStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PagamentoService {
    @Autowired
    OrderService orderService;
    @Autowired
    CarrinhoService carrinhoService;
    @Autowired
    PagamentoRepository pagamentoRepository;
    @Autowired
    ObjectMapper mapper;

    @Transactional
    public Boolean gerarCobranca(String carrinho_id) {
        try {
            Carrinho carrinho = carrinhoService.getById(carrinho_id);
            orderService.criarCobranca(carrinho);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public Pagamento save(Pagamento pagamento) {
        return pagamentoRepository.saveAndFlush(pagamento);

    }


    public void atualizarPagamento(String json) throws JsonProcessingException {
        OrderWebhookNotification orderWebhookNotification = mapper.readValue(json, OrderWebhookNotification.class);

        System.out.println("Chegou aqui: " + orderWebhookNotification);

        OrderWebhookNotification.Data data = orderWebhookNotification.data();

        Order order = orderService.getOrder(data.externalReference());

        System.out.println(order);

        Pagamento pagamento = order.getPagamento();

        if (pagamento == null) {
            throw new IllegalStateException(
                    "Order " + order.getIdOrder() + " não possui Pagamento vinculado");
        }

        List<OrderWebhookNotification.Payment> payments = data.transactions() != null
                ? data.transactions().payments()
                : List.of();
        OrderWebhookNotification.Payment payment = payments.isEmpty() ? null : payments.get(0);

        switch (data.status()) {
            case PROCESSED -> {
                pagamento.setStatus(PagamentoStatus.PROCESSED);
                pagamento.setPaidAt(LocalDateTime.now());

                // TODO: disparar o que precisa acontecer numa venda concluída
                // (imprimir recibo, liberar o carrinho, notificar o terminal, etc.)
            }

            case CANCELED -> {

                pagamento.setStatus(PagamentoStatus.CANCELED); // ajuste o nome do enum se for diferente
                System.out.println("cancelou");
                // TODO: devolver itens reservados do carrinho ao estoque, se aplicável
            }

            case EXPIRED -> {
                pagamento.setStatus(PagamentoStatus.EXPIRED); // ajuste o nome do enum se for diferente

                // TODO: devolver itens reservados do carrinho ao estoque, se aplicável
            }

            case FAILED -> {
                pagamento.setStatus(PagamentoStatus.FAILED); // ajuste o nome do enum se for diferente

                // TODO: avisar o terminal/operador que o pagamento falhou
            }

            default -> {
                // created, at_terminal, action_required, refunded:
                // segue só o espelhamento padrão abaixo, sem ação extra por enquanto.
            }
        }

        pagamento.setStatusDetail(data.statusDetail().getValue());
        pagamento.setUpdatedAt(LocalDateTime.now());

        if (payment != null) {
            pagamento.setTransactionId(payment.id());

            if (payment.paymentMethod() != null) {
                pagamento.setPaymentMethodId(payment.paymentMethod().id());
                pagamento.setTipo(mapTipo(payment.paymentMethod().type()));
                pagamento.setInstallments(payment.paymentMethod().installments());
            }

        }

        order.setMpStatus(data.status());
        order.setMpStatusDetail(data.statusDetail());

        save(pagamento);
        orderService.save(order);
    }

    private PagamentoTipo mapTipo(String mpType) {
        if (mpType == null) {
            return null;
        }
        return switch (mpType) {
            case "credit_card" -> PagamentoTipo.CREDIT_CARD;
            case "debit_card" -> PagamentoTipo.DEBIT_CARD;
            default -> null; // tipo não mapeado, ex: "voucher_card", "bank_transfer", etc.
        };
    }
}
