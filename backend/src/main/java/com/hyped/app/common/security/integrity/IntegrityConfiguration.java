package com.hyped.app.common.security.integrity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IntegrityProperties.class)
public class IntegrityConfiguration {
    @Bean
    IntegrityDecoder integrityDecoder(IntegrityProperties properties, ObjectMapper json) {
        return new GoogleIntegrityDecoder(properties, json);
    }

    @Bean
    IntegrityReplayStore integrityReplayStore(JdbcTemplate jdbc) {
        return new JdbcIntegrityReplayStore(jdbc);
    }

    @Bean
    IntegrityVerifier integrityVerifier(IntegrityProperties properties, IntegrityDecoder decoder,
            IntegrityReplayStore replay, Clock clock) {
        return new IntegrityVerifier(properties, decoder, replay, clock);
    }
}
