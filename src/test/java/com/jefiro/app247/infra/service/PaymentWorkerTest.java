package com.jefiro.app247.infra.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWorkerTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ListOperations<String, String> lists;
    @Mock ValueOperations<String, String> values;
    @Mock PagamentoService pagamentoService;
    @InjectMocks PaymentWorker worker;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(lists);
    }

    @Test
    void confirmaRemocaoDaFilaDeProcessamentoSomenteDepoisDoSucesso() throws Exception {
        String payload = "{\"data\":{\"id\":\"mp-1\"}}";
        when(lists.rightPopAndLeftPush(PaymentWorker.QUEUE, PaymentWorker.PROCESSING_QUEUE))
                .thenReturn(payload);

        worker.processQueue();

        verify(pagamentoService).atualizarPagamento(payload);
        verify(lists).remove(PaymentWorker.PROCESSING_QUEUE, 1, payload);
        verify(lists, never()).leftPush(PaymentWorker.DEAD_LETTER_QUEUE, payload);
    }

    @Test
    void enviaMensagemParaDlqAposLimiteDeTentativas() throws Exception {
        String payload = "{\"status\":\"desconhecido\"}";
        when(lists.rightPopAndLeftPush(PaymentWorker.QUEUE, PaymentWorker.PROCESSING_QUEUE))
                .thenReturn(payload);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L, 2L, 3L);
        doThrow(new IllegalStateException("falha permanente"))
                .when(pagamentoService).atualizarPagamento(payload);

        worker.processQueue();
        worker.processQueue();
        worker.processQueue();

        verify(lists, times(2)).leftPush(PaymentWorker.QUEUE, payload);
        verify(lists).leftPush(PaymentWorker.DEAD_LETTER_QUEUE, payload);
        verify(lists, times(3)).remove(PaymentWorker.PROCESSING_QUEUE, 1, payload);
    }
}
