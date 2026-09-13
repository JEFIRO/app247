package com.jefiro.app247.infra.exception;

import com.jefiro.app247.domain.model.Carrinho;
import com.jefiro.app247.domain.model.Order;
import com.jefiro.app247.domain.model.PaymentAttempt;
import com.jefiro.app247.domain.model.enum_type.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {
    @Test
    void erroDeProdutoMantemCodigoEstruturadoNoContratoHttp() {
        var response = new RestExceptionHandler().noAtoties(
                new ApiBusinessException(
                        HttpStatus.CONFLICT,
                        "PRODUCT_CODE_ALREADY_EXISTS",
                        "Código interno já utilizado."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("PRODUCT_CODE_ALREADY_EXISTS");
        assertThat(response.getBody().getMessage()).isEqualTo("Código interno já utilizado.");
    }

    @Test
    void pagamentoAtivoInformaIdentificadoresParaRecuperacaoDoTerminal() {
        Carrinho cart = new Carrinho();
        cart.setIdCarrinho("cart-active");
        Order order = new Order();
        order.setIdOrder("order-active");
        order.setCarrinho(cart);
        order.setStatus(OrderStatus.CREATED);
        PaymentAttempt attempt = new PaymentAttempt(order);
        attempt.setIdPagamento("attempt-active");
        order.setPagamento(attempt);

        var response = new RestExceptionHandler().paymentAlreadyActive(
                new PaymentAlreadyActiveException(order));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PAYMENT_ALREADY_ACTIVE");
        assertThat(response.getBody().orderId()).isEqualTo("order-active");
        assertThat(response.getBody().paymentAttemptId()).isEqualTo("attempt-active");
        assertThat(response.getBody().cartId()).isEqualTo("cart-active");
        assertThat(response.getBody().paymentStatus()).isEqualTo("WAITING_PAYMENT");
    }
}
