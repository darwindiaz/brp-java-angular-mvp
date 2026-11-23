package co.brp.offers.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import co.brp.offers.application.OfferFollowUpView;
import co.brp.offers.application.OfferFollowUpView.AssignedAnalyst;
import co.brp.offers.domain.Offer.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OfferFollowUpQueries {

    private static final String SELECT = """
            SELECT o.numero_radicacion, brp.code, o.valor_total, o.descuento, o.estado,
                   o.analista_id, analyst.nombre AS analista_nombre,
                   o.creado_en, o.modificado_en, o.escalado_en, o.devuelto_en, o.finalizado_en
            FROM oferta o
            JOIN brp_catalog brp ON brp.id = o.brp_version_id
            LEFT JOIN usuario analyst ON analyst.id = o.analista_id
            WHERE TRUE
            """;

    private final JdbcClient jdbc;

    public OfferFollowUpQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<OfferFollowUpView> list(Long registrantId) {
        return select(registrantId, null).list();
    }

    public Optional<OfferFollowUpView> find(long number, Long registrantId) {
        return select(registrantId, number).optional();
    }

    private JdbcClient.MappedQuerySpec<OfferFollowUpView> select(Long registrantId, Long number) {
        // Los predicados son constantes. El identificador de propietario proviene de la sesión.
        var sql = SELECT
                + (registrantId == null ? "" : " AND o.radicador_id = :registrantId\n")
                + (number == null ? " ORDER BY o.numero_radicacion DESC" : " AND o.numero_radicacion = :number");
        var statement = jdbc.sql(sql);
        if (registrantId != null) {
            statement.param("registrantId", registrantId);
        }
        if (number != null) {
            statement.param("number", number);
        }
        return statement.query((row, index) -> map(row));
    }

    private static OfferFollowUpView map(ResultSet row) throws SQLException {
        var analystId = row.getObject("analista_id", Long.class);
        var analyst = analystId == null ? null : new AssignedAnalyst(analystId, row.getString("analista_nombre"));
        return new OfferFollowUpView(row.getLong("numero_radicacion"), row.getString("code"),
                row.getBigDecimal("valor_total"), row.getBigDecimal("descuento"),
                Status.valueOf(row.getString("estado")), analyst,
                instant(row, "creado_en"), instant(row, "modificado_en"), instant(row, "escalado_en"),
                instant(row, "devuelto_en"), instant(row, "finalizado_en"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var timestamp = row.getObject(column, OffsetDateTime.class);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
