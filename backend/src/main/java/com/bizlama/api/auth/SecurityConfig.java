package com.bizlama.api.auth;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/config",
                                "/api/auth/login",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth ->
                        oauth.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(
            name = "bizlama.auth.mode",
            havingValue = "local",
            matchIfMissing = true
    )
    JwtEncoder localJwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<>(secretKey(properties.tokenSecret()))
        );
    }

    @Bean
    @ConditionalOnProperty(
            name = "bizlama.auth.mode",
            havingValue = "local",
            matchIfMissing = true
    )
    JwtDecoder localJwtDecoder(AuthProperties properties) {
        return NimbusJwtDecoder
                .withSecretKey(secretKey(properties.tokenSecret()))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            name = "bizlama.auth.mode",
            havingValue = "identity-platform"
    )
    JwtDecoder identityPlatformJwtDecoder(
            @Value("${bizlama.auth.project-id}") String projectId
    ) {
        String issuer =
                "https://securetoken.google.com/" + projectId;

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(
                        "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
                )
                .build();

        OAuth2TokenValidator<Jwt> audience = jwt ->
                jwt.getAudience().contains(projectId)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error(
                                        "invalid_token",
                                        "Token audience does not match this BizLaMa project",
                                        null
                                )
                        );

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        audience
                )
        );

        return decoder;
    }

    private SecretKeySpec secretKey(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "BIZLAMA_AUTH_TOKEN_SECRET must contain at least 32 bytes"
            );
        }

        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}