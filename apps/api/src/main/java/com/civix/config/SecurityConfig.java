package com.civix.config;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.civix.auth.repository.UserRepository;
import com.civix.auth.service.ActiveUserJwtAuthenticationConverter;
import com.civix.auth.service.JwtService;
import com.civix.common.security.CookieBearerTokenResolver;
import com.civix.common.security.CsrfCookieFilter;
import com.civix.common.security.InternalTokenFilter;
import com.civix.common.security.ProblemAccessDeniedHandler;
import com.civix.common.security.ProblemAuthenticationEntryPoint;
import com.civix.common.security.SecurityProblemWriter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import tools.jackson.databind.ObjectMapper;

/**
 * Two stateless filter chains:
 * <ol>
 *   <li>{@code /internal/**} — agent callbacks, static service token ({@code API_INTERNAL_TOKEN}), no CSRF.</li>
 *   <li>everything else — HS256 JWT from the {@code civix_at} cookie or {@code Authorization: Bearer},
 *       cookie-based CSRF (SPA pattern) for state-changing requests.</li>
 * </ol>
 * {@code /actuator/prometheus} is permitted here and must be restricted at the network level
 * (it is not routed by the web proxy; only the internal docker network can reach it).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] CSRF_EXEMPT = {"/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh"};

    @Bean
    public SecretKey jwtSigningKey(CivixProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32 || secret.startsWith("CHANGE_ME")) {
            throw new IllegalStateException("JWT_SECRET must be set to at least 32 bytes of random data");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtService.ISSUER));
        return decoder;
    }

    /**
     * PBKDF2 (310k iterations, SHA-256) as the default: unlike BCrypt it has no 72-byte input limit, and the API
     * allows passwords up to 128 characters. BCrypt hashes remain verifiable through the delegating encoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        String defaultId = "pbkdf2@SpringSecurity_v5_8";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put(defaultId, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        return new DelegatingPasswordEncoder(defaultId, encoders);
    }

    @Bean
    public SecurityProblemWriter securityProblemWriter(ObjectMapper objectMapper) {
        return new SecurityProblemWriter(objectMapper);
    }

    @Bean
    public ActiveUserJwtAuthenticationConverter activeUserJwtAuthenticationConverter(UserRepository users) {
        return new ActiveUserJwtAuthenticationConverter(users);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, CivixProperties props,
                                                           SecurityProblemWriter problems) throws Exception {
        ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(problems);
        http.securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalTokenFilter(props.internal().apiToken(), problems),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("INTERNAL"))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new ProblemAccessDeniedHandler(problems)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CivixProperties props, JwtDecoder jwtDecoder,
                                                      ActiveUserJwtAuthenticationConverter jwtConverter,
                                                      SecurityProblemWriter problems) throws Exception {
        ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(problems);
        ProblemAccessDeniedHandler deniedHandler = new ProblemAccessDeniedHandler(problems);

        boolean secureCookies = props.cookie().secure();
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(c -> c.secure(secureCookies).sameSite("Lax").path("/"));
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Browsers never attach an Authorization header cross-site on their own; such requests need no CSRF token.
        RequestMatcher bearerHeaderRequest = request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null;

        http.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler)
                .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                .ignoringRequestMatchers(CSRF_EXEMPT)
                        .ignoringRequestMatchers(bearerHeaderRequest))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .headers(h -> h.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/services", "/api/v1/services/categories",
                                "/api/v1/services/jurisdictions", "/api/v1/services/{id}").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));
        return http.build();
    }
}
