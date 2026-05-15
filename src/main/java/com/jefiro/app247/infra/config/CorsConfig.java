package com.jefiro.app247.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);

        // Libera qualquer origem
        config.setAllowedOriginPatterns(List.of("*"));

        // Libera qualquer método
        config.setAllowedMethods(List.of("*"));

        // Libera qualquer header
        config.setAllowedHeaders(List.of("*"));

        // Expõe qualquer header
        config.setExposedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}