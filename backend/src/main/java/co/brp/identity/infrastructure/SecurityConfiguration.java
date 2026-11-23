package co.brp.identity.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import co.brp.identity.application.IdentityAuthentication;
import co.brp.identity.domain.IdentityUser;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    private static final Duration ISSUED_AT_TOLERANCE = Duration.ofSeconds(5);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, IdentityStore store) throws Exception {
        var bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        var bearer = new BearerTokenAuthenticationConverter();
        var health = EndpointRequest.to(HealthEndpoint.class);
        var login = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/auth/login");
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    if (request.getServletPath().startsWith("/api/")) {
                        bearerEntryPoint.commence(request, response, exception);
                    } else {
                        response.setStatus(403);
                    }
                }))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(health, login).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers("/api/admin/**").access((authentication, request) ->
                                new AuthorizationDecision(authentication.get().getPrincipal() instanceof IdentityUser user
                                        && user.canAdminister()))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .authenticationConverter(request -> health.matches(request) || login.matches(request)
                                ? null : bearer.convert(request))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    long id;
                    try {
                        id = Long.parseLong(token.getSubject());
                    } catch (NumberFormatException exception) {
                        throw new OAuth2AuthenticationException("invalid_token");
                    }
                    var user = store.find(id).filter(candidate -> candidate.active())
                            .orElseThrow(() -> new OAuth2AuthenticationException("invalid_token"));
                    var authorities = new ArrayList<SimpleGrantedAuthority>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
                    user.modules().forEach(module -> authorities.add(new SimpleGrantedAuthority("MODULE_" + module.name())));
                    return UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
                })))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretKey jwtSecretKey(Environment environment) {
        try {
            var value = environment.getRequiredProperty("BRP_JWT_SECRET_BASE64");
            var bytes = Base64.getDecoder().decode(value);
            if (bytes.length >= 32) {
                return new SecretKeySpec(bytes, "HmacSHA256");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // No propagar el valor configurado ni el error del decodificador.
        }
        throw new IllegalStateException("BRP_JWT_SECRET_BASE64 requiere una clave Base64 de al menos 32 bytes.");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secret));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secret, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(secret).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator(IdentityAuthentication.ISSUER), token -> {
                    var issuedAt = token.getIssuedAt();
                    var expiresAt = token.getExpiresAt();
                    var now = clock.instant();
                    if (issuedAt == null || expiresAt == null || !now.isBefore(expiresAt)
                            || !expiresAt.isAfter(issuedAt)
                            || issuedAt.isAfter(now.plus(ISSUED_AT_TOLERANCE))
                            || Duration.between(issuedAt, expiresAt).compareTo(IdentityAuthentication.TOKEN_LIFETIME) > 0
                            || token.getAudience() == null || !token.getAudience().contains(IdentityAuthentication.AUDIENCE)) {
                        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                    }
                    return OAuth2TokenValidatorResult.success();
                }));
        return decoder;
    }
}
