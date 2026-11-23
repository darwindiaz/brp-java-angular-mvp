package co.brp.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import co.brp.identity.domain.IdentityUser;
import co.brp.identity.infrastructure.IdentityStore;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityAuthentication {

    public static final String ISSUER = "brp-backend";
    public static final String AUDIENCE = "brp-api";
    public static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final IdentityStore store;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final LoginAttemptLimiter attempts;
    private final String dummyHash;

    public IdentityAuthentication(IdentityStore store, PasswordEncoder passwords, JwtEncoder encoder, Clock clock,
                                  LoginAttemptLimiter attempts) {
        this.store = store;
        this.passwords = passwords;
        this.encoder = encoder;
        this.clock = clock;
        this.attempts = attempts;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public AccessToken login(String login, String password, String source) {
        if (!attempts.tryAcquire(source)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Intenta de nuevo más tarde.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        var credentials = store.credentials(IdentityUser.normalizeLogin(login));
        var hash = credentials.map(IdentityStore.Credentials::hash).orElse(dummyHash);
        var matches = passwords.matches(password, hash);
        if (!matches || credentials.filter(IdentityStore.Credentials::active).isEmpty()) {
            throw invalidCredentials();
        }

        var now = clock.instant();
        var claims = JwtClaimsSet.builder().subject(Long.toString(credentials.orElseThrow().id()))
                .issuer(ISSUER).audience(List.of(AUDIENCE)).issuedAt(now).expiresAt(now.plus(TOKEN_LIFETIME)).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new AccessToken(token.getTokenValue(), "Bearer", TOKEN_LIFETIME.toSeconds());
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
    }

    public record AccessToken(String accessToken, String tokenType, long expiresIn) {
        @Override
        public String toString() {
            return "AccessToken[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
        }
    }
}
