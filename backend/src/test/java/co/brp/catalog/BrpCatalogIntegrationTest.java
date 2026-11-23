package co.brp.catalog;

import java.time.LocalDate;

import co.brp.catalog.application.BrpCatalog;
import co.brp.catalog.domain.BrpStatus;
import co.brp.catalog.domain.BrpVersion;
import co.brp.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
@SpringBootTest
@Transactional
class BrpCatalogIntegrationTest {

    @Autowired
    private BrpCatalog catalog;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loadsOriginalCatalogAndNormalizesUnpricedAssets() {
        assertThat(catalog.current()).hasSize(100)
                .extracting(BrpVersion::getCode).doesNotHaveDuplicates();
        assertThat(catalog.available()).hasSize(56);
        var unpriced = catalog.current().stream()
                .filter(brp -> brp.getStatus() == BrpStatus.SIN_PRECIO)
                .toList();
        assertThat(unpriced).hasSize(10).allSatisfy(brp -> {
            assertThat(brp.getPrice()).isNull();
            assertThat(brp.getPriceAssignedOn()).isNull();
        }).extracting(BrpVersion::getCode)
                .contains("BRP-0045", "BRP-0052", "BRP-0064", "BRP-0084", "BRP-0098");
        assertThat(catalog.latestIngestionDate()).contains(LocalDate.of(2026, 11, 17));
        assertThat(catalog.currentByCode("BRP-0001")).hasValueSatisfying(brp -> {
            assertThat(brp.getPrice()).isEqualByComparingTo("320000000");
            assertThat(brp.getDescription()).contains("172 m²", "Medellín");
        });
    }

    @Test
    void laterBlockedVersionHidesHistoricalAvailability() {
        var code = "BRP-0001";
        var latest = LocalDate.of(2026, 11, 18);
        assertThat(catalog.available()).extracting(BrpVersion::getCode).contains(code);
        jdbc.update("""
                insert into brp_catalog (
                    code, ingestion_date, status, asset_type, property_registration,
                    department, municipality, description, price, price_assigned_on
                )
                select code, ?, 'SANEAMIENTO', asset_type, property_registration,
                    department, municipality, description, price, price_assigned_on
                from brp_catalog where code = ?
                """, latest, code);

        assertThat(catalog.current()).hasSize(100);
        assertThat(catalog.available()).hasSize(55)
                .extracting(BrpVersion::getCode).doesNotContain(code);
        assertThat(catalog.currentByCode(code)).hasValueSatisfying(brp -> {
            assertThat(brp.getIngestionDate()).isEqualTo(latest);
            assertThat(brp.getStatus()).isEqualTo(BrpStatus.SANEAMIENTO);
        });
        assertThat(catalog.latestIngestionDate()).contains(latest);
    }

    @Test
    void rejectsDuplicateCodeAndIngestionDate() {
        assertThatThrownBy(() -> jdbc.update("""
                update brp_catalog set code = 'BRP-0001', ingestion_date = date '2026-11-10'
                where code = 'BRP-0002'
                """)).isInstanceOf(DuplicateKeyException.class).hasMessageContaining("brp_code_ingestion_uk");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "CASA, MAT-01, ABC123",
            "CASA, NULL, NULL",
            "VEHICULO, NULL, NULL",
            "VEHICULO, MAT-01, NULL",
            "MAQUINARIA, MAT-01, NULL",
            "MAQUINARIA, NULL, ABC123"
    }, nullValues = "NULL")
    void rejectsInvalidRegistrationForAssetType(String type, String registration, String plate) {
        assertThatThrownBy(() -> jdbc.update("""
                update brp_catalog set asset_type = ?, property_registration = ?, license_plate = ?
                where code = 'BRP-0001'
                """, type, registration, plate))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("brp_registration_ck");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "BRP-0001, 001-554854, NULL",
            "BRP-0007, NULL, DGH105",
            "BRP-0009, NULL, NULL"
    }, nullValues = "NULL")
    void acceptsValidIdentifiersAndNullsByAssetType(String code, String registration, String plate) {
        assertThat(jdbc.update("""
                update brp_catalog set property_registration = ?, license_plate = ?
                where code = ?
                """, registration, plate, code)).isEqualTo(1);
        assertThat(catalog.currentByCode(code)).hasValueSatisfying(brp -> {
            assertThat(brp.getPropertyRegistration()).isEqualTo(registration);
            assertThat(brp.getLicensePlate()).isEqualTo(plate);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", " \t "})
    void rejectsBlankCode(String value) {
        assertThatThrownBy(() -> jdbc.update(
                "update brp_catalog set code = ? where code = 'BRP-0001'", value))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("brp_code_nonblank_ck");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", " \t "})
    void rejectsBlankPropertyRegistration(String value) {
        assertThatThrownBy(() -> jdbc.update(
                "update brp_catalog set property_registration = ? where code = 'BRP-0001'", value))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("brp_property_registration_nonblank_ck");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", " \t "})
    void rejectsBlankLicensePlate(String value) {
        assertThatThrownBy(() -> jdbc.update(
                "update brp_catalog set license_plate = ? where code = 'BRP-0007'", value))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("brp_license_plate_nonblank_ck");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "price = null | brp_price_pair_ck",
            "price_assigned_on = null | brp_price_pair_ck",
            "price = null, price_assigned_on = null | brp_unpriced_status_ck",
            "price = 0 | brp_positive_price_ck",
            "price = -1 | brp_positive_price_ck"
    })
    void rejectsInvalidPrice(String assignment, String constraint) {
        // Las asignaciones son SQL literal de esta prueba, sin entradas externas.
        var sql = "update brp_catalog set " + assignment + " where code = 'BRP-0001'";
        assertThatThrownBy(() -> jdbc.update(sql))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }
}
