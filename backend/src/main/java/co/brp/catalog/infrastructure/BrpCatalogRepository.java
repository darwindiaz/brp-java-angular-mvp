package co.brp.catalog.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import co.brp.catalog.domain.BrpStatus;
import co.brp.catalog.domain.BrpVersion;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface BrpCatalogRepository extends Repository<BrpVersion, Long> {

    String CURRENT_VERSION = """
            select brp from BrpVersion brp
            where not exists (
                select newer.id from BrpVersion newer
                where newer.code = brp.code and newer.ingestionDate > brp.ingestionDate
            )
            """;

    @Query(CURRENT_VERSION + "order by brp.code")
    List<BrpVersion> findCurrent();

    @Query(CURRENT_VERSION + "and brp.status = :status order by brp.code")
    List<BrpVersion> findCurrentByStatus(@Param("status") BrpStatus status);

    @Query(CURRENT_VERSION + "and brp.code = :code")
    Optional<BrpVersion> findCurrentByCode(@Param("code") String code);

    @Query("select max(brp.ingestionDate) from BrpVersion brp")
    Optional<LocalDate> findLatestIngestionDate();
}
