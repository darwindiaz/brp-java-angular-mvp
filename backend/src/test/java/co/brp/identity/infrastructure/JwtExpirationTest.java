package co.brp.identity.infrastructure;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;

import co.brp.identity.application.IdentityAuthentication;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtExpirationTest {

    @ParameterizedTest(name = "Desfase frente a exp: {0} ns, válido: {1}")
    @CsvSource({"-1, true", "0, false", "1, false"})
    void acceptsOnlyInstantsStrictlyBeforeExpiration(long offsetNanos, boolean valid) {
        var expiresAt = Instant.parse("2026-11-22T16:15:00Z");
        var issuedAt = expiresAt.minus(IdentityAuthentication.TOKEN_LIFETIME);
        var clock = Clock.fixed(expiresAt.plusNanos(offsetNanos), ZoneOffset.UTC);
        var keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        var key = new SecretKeySpec(keyBytes, "HmacSHA256");
        var security = new SecurityConfiguration();
        var claims = JwtClaimsSet.builder().subject("1")
                .issuer(IdentityAuthentication.ISSUER).audience(List.of(IdentityAuthentication.AUDIENCE))
                .issuedAt(issuedAt).expiresAt(expiresAt).build();
        var token = security.jwtEncoder(key)
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        var decoder = security.jwtDecoder(key, clock);

        if (valid) {
            assertThat(decoder.decode(token).getExpiresAt()).isEqualTo(expiresAt);
        } else {
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
        }
    }
}
