package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.MercadoPagoConta;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.dto.OrderResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.OrderRequest;
import com.jefiro.app247.infra.event.MercadoPagoCobrancaEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation; // <--- Import do Enum
import org.springframework.transaction.annotation.Transactional; // <--- Import do @Transactional do Spring
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class MercadoPagoCobrancaService {

    private static final String URL = "https://api.mercadopago.com/v1/orders";

    @Autowired
    OauthMercadoPagoService oauthMercadoPagoService;
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    OrderService orderService;
    @Autowired
    PagamentoService pagamentoService;

    @EventListener
    public void ouvinte(MercadoPagoCobrancaEvent event) {
        try {
            System.out.println(">>> INICIANDO PROCESSAMENTO MP PARA ORDER: " + event.getOrder().getIdOrder());
            newOrder(event.getOrder());
            System.out.println(">>> FINALIZADO MP COM SUCESSO!");
        } catch (Throwable e) {
            System.err.println(">>> ERRO FATAL DENTRO DO OUVINTE MP:");
            e.printStackTrace(); // <--- ESTE PRINT MOSTRARÁ O MOTIVO DO ROLLBACK
        }
    }

    private void newOrder(Order order) {

        System.out.println("new order");
        if (EmpresaContext.get() == null) {
            throw new RuntimeException();
        }

        MercadoPagoConta mercadoPagoConta = oauthMercadoPagoService.getByEmpresa(EmpresaContext.get());

        OrderRequest request = new OrderRequest(
                "point",
                order.getIdOrder(),
                "PT5M",
                "Venda PDV",
                new OrderRequest.TransactionsRequest(
                        List.of(new OrderRequest.PaymentRequest(String.valueOf(order.getTotal())))
                ),
                new OrderRequest.ConfigRequest(
                        new OrderRequest.PointRequest(mercadoPagoConta.getTerminalId(), "no_ticket")
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(mercadoPagoConta.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", order.getIdOrder());

        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                    URL,
                    HttpMethod.POST,
                    entity,
                    OrderResponse.class);
            OrderResponse orderResponse = response.getBody();

            // 1. Atualiza dados da Order
            order.setMpOrderId(orderResponse.id());
            order.setMpType(orderResponse.type());
            order.setStatus(orderResponse.status());
            order.setMpUserId(orderResponse.userId());
            order.setMpStatus(orderResponse.status());
            order.setMpStatusDetail(orderResponse.statusDetail());
            if (orderResponse.config() != null && orderResponse.config().point() != null) {
                order.setMpTerminalId(orderResponse.config().point().terminalId());
            }
            System.out.println("chegou aqui");
            // 2. Instancia e garante preenchimento de FKs obrigatórias do Pagamento
            Pagamento pagamento = new Pagamento(order, orderResponse);
            pagamento.setEmpresa(order.getEmpresa()); // <--- CRUCIAL: empresa_id não pode ser NULL no DB
            pagamento.setOrder(order);                // <--- Vincula ao pedido

            // 3. Persiste via Repository diretamente com saveAndFlush
            pagamento = pagamentoService.save(pagamento);

            // 4. Vincula de volta na Order e atualiza
            order.setPagamento(pagamento);
            orderService.save(order);

            System.out.println("Pagamento salvo com sucesso: " + pagamento.getIdPagamento());

        } catch (HttpClientErrorException e) {
            System.out.println("Erro MP: " + e.getResponseBodyAsString());
            throw e;
        }
    }


//    private void newOrder(Order order) {
//
//
//        MercadoPagoConta mercadoPagoConta = oauthMercadoPagoService.getByEmpresa(EmpresaContext.get());
//
//
//        OrderRequest request =
//                new OrderRequest(
//                        "point",
//                        order.getIdOrder(),
//                        "PT5M",
//                        "Venda PDV",
//                        new TransactionsRequest(
//                                List.of(
//                                        new PaymentRequest(String.valueOf(order.getTotal()))
//                                )
//                        ),
//                        new ConfigRequest(
//                                new PointRequest(mercadoPagoConta.getTerminalId(), "no_ticket")
//                        )
//                );
//
//        HttpHeaders headers = new HttpHeaders();
//
//        headers.setBearerAuth(mercadoPagoConta.getAccessToken());
//
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        headers.set(
//                "X-Idempotency-Key",
//                order.getIdOrder()
//        );
//
//        HttpEntity<OrderRequest> entity =
//                new HttpEntity<>(request, headers);
//
//        try {
//
//            ResponseEntity<OrderResponse> response =
//                    restTemplate.exchange(
//                            URL,
//                            HttpMethod.POST,
//                            entity,
//                            OrderResponse.class);
//
//            System.out.println(response.getBody());
//
//        } catch (HttpClientErrorException e) {
//
//            System.out.println(e.getResponseBodyAsString());
//
//            throw e;
//        }
//    }


}
