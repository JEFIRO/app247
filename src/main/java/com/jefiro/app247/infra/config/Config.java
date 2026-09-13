package com.jefiro.app247.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class Config {

    @Bean
    public ObjectMapper mapper() {
        return new ObjectMapper();
    }
    @Bean
    @Primary
    public RestTemplate restTemplate(
            @Value("${http.client.connect-timeout:5s}") Duration connectTimeout,
            @Value("${http.client.read-timeout:15s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    @Bean
    @Qualifier("comprovanteRestTemplate")
    public RestTemplate comprovanteRestTemplate(
            @Value("${app.comprovante.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.comprovante.read-timeout:35s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
