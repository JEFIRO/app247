package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.Pagamento;
import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.PagamentoResponse;
import com.jefiro.app247.domain.model.dto.mercadopago.PreferenceReturn;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import com.jefiro.app247.domain.model.enum_type.PagamentoTipo;
import com.jefiro.app247.infra.event.PaymentEvent;
import com.jefiro.app247.infra.repository.PagamentoRepository;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.common.PhoneRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.preference.*;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MercadoPagoService {

    @Autowired
    private PagamentoRepository pagamentoRepository;
    @Autowired
    OrderService orderService;
    @Autowired
    ApplicationEventPublisher publisher;

    @Value("${api.mercado.pago.access.token}")
    private String accessToken;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    public PagamentoResponse criarPix(@NonNull Order order) {

        try {

            PaymentClient client = new PaymentClient();

            PaymentCreateRequest request =
                    PaymentCreateRequest.builder()
                            .transactionAmount(
                                    order.getTotal()
                            )
                            .description("Pedido " + order.getIdOrder())
                            .paymentMethodId("pix")
                            .dateOfExpiration(OffsetDateTime.now().plusMinutes(10))
                            .payer(
                                    PaymentPayerRequest.builder()
                                            .email("teste@test.com")
                                            .build()
                            )
                            .build();

            Payment payment = client.create(request);

            order.setStatus(OrderStatus.PENDING);

            Pagamento pagamento = new Pagamento(order, PagamentoTipo.PIX, payment);

            pagamentoRepository.save(pagamento);

            String qrCode = null;
            String qrCodeBase64 = null;

            if (payment.getPointOfInteraction() != null &&
                    payment.getPointOfInteraction().getTransactionData() != null) {

                qrCode = payment.getPointOfInteraction()
                        .getTransactionData()
                        .getQrCode();

                qrCodeBase64 = payment.getPointOfInteraction()
                        .getTransactionData()
                        .getQrCodeBase64();
            }

            return new PagamentoResponse(
                    pagamento.getIdPagamento(),
                    order.getIdOrder(),
                    pagamento.getValor(),
                    pagamento.getTipo(),
                    pagamento.getStatus(),
                    pagamento.getTransactionId(),
                    qrCode,
                    qrCodeBase64
            );

        } catch (MPApiException ex) {

            System.out.println("STATUS: " + ex.getApiResponse().getStatusCode());
            System.out.println("CONTENT: " + ex.getApiResponse().getContent());

            throw new RuntimeException(ex);

        } catch (MPException ex) {

            ex.printStackTrace();

            throw new RuntimeException(ex);
        }
    }


    public PreferenceReturn criarCheckout(Order order) throws Exception {

        List<PreferenceItemRequest> items = order.getCarrinho().getItems().stream().
                map(item -> PreferenceItemRequest.builder()
                        .id(item.getIdItem())
                        .title(item.getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .currencyId("BRL")
                        .build()).toList();

        PreferenceBackUrlsRequest backUrls =
                PreferenceBackUrlsRequest.builder()
                        .success("meuapp://success")
                        .pending("meuapp://pending")
                        .failure("meuapp://failure")
                        .build();

        PreferencePaymentMethodsRequest paymentMethods =
                PreferencePaymentMethodsRequest.builder()
                        .excludedPaymentMethods(new ArrayList<>())
                        .excludedPaymentTypes(new ArrayList<>())
                        .installments(12)
                        .build();

        PreferenceRequest.PreferenceRequestBuilder requestBuilder =
                PreferenceRequest.builder()
                        .items(items)
                        .externalReference(order.getIdOrder())
                        .backUrls(backUrls)
                        .autoReturn("approved")
                        .paymentMethods(paymentMethods);

        if (order.getUser() != null) {

            User user = order.getUser();

            PreferencePayerRequest payer =
                    PreferencePayerRequest.builder()
                            .name(user.getNome())
                            .surname(user.getSobrenome())
                            .email(user.getEmail())
                            .phone(
                                    PhoneRequest.builder()
                                            .number(user.getTelefone())
                                            .build()
                            )
                            .identification(
                                    IdentificationRequest.builder()
                                            .type("CPF")
                                            .number(user.getCpf())
                                            .build()
                            )
                            .build();

            requestBuilder.payer(payer);
        }

        PreferenceRequest request = requestBuilder.build();

        PreferenceClient client = new PreferenceClient();

        Preference preference = client.create(request);

        return new PreferenceReturn(preference.getId(), preference.getInitPoint());
    }


//    @Transactional
//    public void atualizarPagamento(String paymentId) {
//
//        try {
//
//            PaymentClient client = new PaymentClient();
//            Payment payment = client.get(Long.parseLong(paymentId));
//
//            Pagamento pagamento = pagamentoRepository
//                    .findByTransactionId(paymentId)
//                    .orElseThrow(() -> new RuntimeException("Pagamento não encontrado"));
//
//            if (pagamento.getStatus() == PagamentoStatus.APPROVED) {
//                return;
//            }
//
//            String status = payment.getStatus();
//
//            PagamentoStatus novoStatus = switch (status) {
//                case "approved" -> PagamentoStatus.APPROVED;
//                case "rejected" -> PagamentoStatus.DENIED;
//                case "cancelled" -> PagamentoStatus.CANCELLED;
//                case "in_process" -> PagamentoStatus.PROCESSING;
//                default -> PagamentoStatus.PENDING;
//            };
//
//            if (pagamento.getStatus() == novoStatus) {
//                return;
//            }
//
//            pagamento.setStatus(novoStatus);
//            pagamento.setUpdatedAt(LocalDateTime.now());
//
//            pagamento.setPaymentMethodId(payment.getPaymentMethodId());
//            pagamento.setStatusDetail(payment.getStatusDetail());
//            pagamento.setTransactionId(payment.getId().toString());
//
//            if (payment.getTransactionDetails() != null) {
//                pagamento.setNsu(payment.getTransactionDetails().getExternalResourceUrl());
//                pagamento.setAuthorizationCode(payment.getAuthorizationCode());
//            }
//
//            Order order = pagamento.getOrder();
//
//            if (order != null) {
//
//                switch (novoStatus) {
//                    case APPROVED -> order.setStatus(OrderStatus.PAID);
//                    case PROCESSING -> order.setStatus(OrderStatus.PROCESSING);
//                    case CANCELLED, DENIED -> order.setStatus(OrderStatus.CANCELLED);
//                    default -> order.setStatus(OrderStatus.PENDING);
//                }
//
//                order = orderService.save(order);
//            }
//
//            if (novoStatus == PagamentoStatus.APPROVED) {
//
//                pagamento.setPaidAt(LocalDateTime.now());
//
//                assert order != null;
//
//                PaymentEvent event = new PaymentEvent(order.getIdTerminal(), order.getIdOrder(), pagamento.getTransactionId(), "PAID");
//
//                publisher.publishEvent(event);
//
//            }
//            pagamento.setUpdatedAt(LocalDateTime.now());
//            pagamentoRepository.save(pagamento);
//
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

    public void sendEvent() {
        PaymentEvent event = new PaymentEvent("095a0e6c-47cf-4d94-a154-f8b46f1846dc", "c5d08227-e92d-42b5-ad04-7c8bcf7a0d78", "164173963104", "PAID");

        publisher.publishEvent(event);
    }
}