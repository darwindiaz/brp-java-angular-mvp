package co.brp.offers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import co.brp.catalog.application.BrpCatalog;
import co.brp.identity.domain.IdentityUser;
import co.brp.offers.application.OfferRegistration;
import co.brp.offers.domain.Offer;
import co.brp.testing.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@SpringBootTest
@Transactional
class OfferIntegrationTest {

    private static final String BRP_CODE = "BRP-0001";

    @Autowired
    private OfferRegistration registration;
    @Autowired
    private BrpCatalog catalog;
    @Autowired
    private EntityManager entities;
    @Autowired
    private JdbcTemplate jdbc;

    private long registrantId;

    @BeforeEach
    void authenticateRegistrant() {
        registrantId = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, identificador_acceso, password_hash, rol)
                SELECT 'Radicador de pruebas', 'offer-test', password_hash, 'RADICADOR'
                FROM usuario WHERE identificador_acceso = 'admin'
                RETURNING id
                """, Long.class);
        var user = new IdentityUser(registrantId, "Radicador de pruebas", "offer-test",
                IdentityUser.Role.RADICADOR, true, Instant.EPOCH, Instant.EPOCH,
                List.of(IdentityUser.Module.RADICACION));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void migratesAndPersistsAggregateWithItsExactHistoricalBrp() {
        assertThat(jdbc.queryForList("SELECT id FROM databasechangelog", String.class))
                .contains("006-create-offers");
        var original = catalog.currentByCode(BRP_CODE).orElseThrow();
        var offer = register();
        ingestNewVersion("DISPONIBLE");

        entities.flush();
        entities.clear();
        var stored = entities.find(Offer.class, offer.id());
        assertThat(stored.registrationNumber()).isPositive();
        assertThat(stored.status()).isEqualTo(Offer.Status.RADICADO);
        assertThat(stored.registrantId()).isEqualTo(registrantId);
        assertThat(stored.brpVersionId()).isEqualTo(original.getId());
        assertThat(stored.referencePrice()).isEqualByComparingTo(original.getPrice());
        assertThat(stored.referencePrice()).isNotEqualByComparingTo(
                catalog.currentByCode(BRP_CODE).orElseThrow().getPrice());
        assertThat(stored.total()).isEqualByComparingTo("160000000.00");
        assertThat(stored.discount()).isEqualByComparingTo("50.0000");
        assertThat(stored.participations()).singleElement().satisfies(participation ->
                assertThat(participation.payments()).singleElement().satisfies(payment ->
                        assertThat(payment.amount()).isEqualByComparingTo("160000000.00")));
    }

    @Test
    void assignsDistinctNumbersForMultipleOffersOnTheSameBrp() {
        var first = register();
        var second = register();
        assertThat(first.registrationNumber()).isPositive();
        assertThat(second.registrationNumber()).isGreaterThan(first.registrationNumber());
        assertThat(second.brpVersionId()).isEqualTo(first.brpVersionId());
    }

    @Test
    void rejectsUnavailableCurrentVersionDespiteAvailableHistory() {
        ingestNewVersion("SANEAMIENTO");
        assertThatThrownBy(this::register).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresAnAuthenticatedRegistrant() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(this::register).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE oferta SET precio_referencia = 0 WHERE id = ?",
            "UPDATE oferta SET valor_total = 'NaN' WHERE id = ?",
            "UPDATE oferta SET estado = 'INVALIDO' WHERE id = ?",
            "UPDATE oferta SET brp_version_id = -1 WHERE id = ?",
            "UPDATE oferta SET radicador_id = -1 WHERE id = ?",
            """
            UPDATE oferta AS target SET numero_radicacion = (
                SELECT min(other.numero_radicacion) FROM oferta AS other
                WHERE other.radicador_id = target.radicador_id
            ) WHERE target.id = ?
            """,
            "UPDATE oferta_participacion SET porcentaje = 0 WHERE oferta_id = ?",
            "INSERT INTO oferta_participacion (oferta_id, tipo_identificacion, numero_identificacion, nombre, porcentaje) VALUES (?, 'NIT', '123', 'Otro', 10)",
            "UPDATE oferta_pago SET financiadora = 'Entidad' WHERE participacion_id IN (SELECT id FROM oferta_participacion WHERE oferta_id = ?)",
            "UPDATE oferta_pago SET valor = -1 WHERE participacion_id IN (SELECT id FROM oferta_participacion WHERE oferta_id = ?)"
    })
    void enforcesDatabaseConstraints(String sql) {
        register();
        var second = register();
        entities.flush();
        assertThatThrownBy(() -> jdbc.update(sql, second.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Offer register() {
        var payment = new Offer.PaymentTerms(Offer.PaymentMethod.CONTADO, LocalDate.of(2026, 12, 1),
                new BigDecimal("160000000.00"), null);
        var bidder = new Offer.Bidder(Offer.IdentificationType.CC, "123", "Oferente", null,
                new BigDecimal("100.0000"), List.of(payment));
        return registration.register(BRP_CODE, List.of(bidder));
    }

    private void ingestNewVersion(String status) {
        jdbc.update("""
                INSERT INTO brp_catalog (code, ingestion_date, status, asset_type, property_registration,
                    license_plate, department, municipality, description, price, price_assigned_on)
                SELECT code, DATE '2027-01-01', ?, asset_type, property_registration,
                    license_plate, department, municipality, description, price + 1, price_assigned_on
                FROM brp_catalog WHERE id = ?
                """, status, catalog.currentByCode(BRP_CODE).orElseThrow().getId());
    }
}
