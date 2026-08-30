package com.jefiro.app247.infra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jefiro.app247.domain.model.enum_type.ProdutoCatalogChangeReason;
import com.jefiro.app247.infra.event.ProdutoCatalogChangedEvent;
import com.jefiro.app247.infra.event.ProdutoSyncRequiredMessage;
import com.jefiro.app247.infra.repository.TerminalRepository;
import com.jefiro.app247.infra.websocket.PaymentWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ProdutoCatalogNotificationAfterCommitTest.Config.class)
class ProdutoCatalogNotificationAfterCommitTest {
    @Autowired ApplicationEventPublisher publisher;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TerminalRepository terminalRepository;
    @Autowired PaymentWebSocketHandler webSocketHandler;

    @BeforeEach
    void resetarMocks() {
        reset(terminalRepository, webSocketHandler);
        when(terminalRepository.findIdsByCondominiumIds(anyCollection()))
                .thenReturn(List.of("terminal-a", "terminal-b"));
        when(webSocketHandler.sendToTerminal(anyString(), any())).thenReturn(true);
    }

    @Test
    void rollbackNaoEnviaNotificacao() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishEvent(evento());
            verifyNoInteractions(terminalRepository, webSocketHandler);
            status.setRollbackOnly();
        });

        verifyNoInteractions(terminalRepository, webSocketHandler);
    }

    @Test
    void commitEnviaSomenteAosTerminaisDosCondominiosDoEvento() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            publisher.publishEvent(evento());
            verifyNoInteractions(terminalRepository, webSocketHandler);
        });

        verify(terminalRepository).findIdsByCondominiumIds(Set.of("cond-a"));
        verify(webSocketHandler).sendToTerminal(eq("terminal-a"), isA(ProdutoSyncRequiredMessage.class));
        verify(webSocketHandler).sendToTerminal(eq("terminal-b"), isA(ProdutoSyncRequiredMessage.class));
        verifyNoMoreInteractions(webSocketHandler);
    }

    private ProdutoCatalogChangedEvent evento() {
        return new ProdutoCatalogChangedEvent("prod-a",
                ProdutoCatalogChangeReason.PRODUCT_UPDATED, Set.of("cond-a"));
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .generateUniqueName(true)
                    .build();
        }

        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean TerminalRepository terminalRepository() {
            return mock(TerminalRepository.class);
        }

        @Bean PaymentWebSocketHandler webSocketHandler() {
            return mock(PaymentWebSocketHandler.class);
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean ProdutoCatalogNotificationService produtoCatalogNotificationService() {
            return new ProdutoCatalogNotificationService();
        }
    }
}
