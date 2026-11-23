package co.brp.catalog.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import co.brp.catalog.domain.BrpStatus;
import co.brp.catalog.domain.BrpVersion;
import co.brp.catalog.infrastructure.BrpCatalogRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrpCatalogTest {

    private final BrpCatalogRepository repository = mock(BrpCatalogRepository.class);
    private final BrpCatalog catalog = new BrpCatalog(repository);

    @Test
    void requestsOnlyCurrentAvailableVersions() {
        var available = mock(BrpVersion.class);
        when(repository.findCurrentByStatus(BrpStatus.DISPONIBLE)).thenReturn(List.of(available));

        assertThat(catalog.available()).containsExactly(available);
    }

    @Test
    void returnsGlobalIngestionDateIndependentlyOfAvailability() {
        var date = LocalDate.of(2026, 11, 17);
        when(repository.findLatestIngestionDate()).thenReturn(Optional.of(date));

        assertThat(catalog.latestIngestionDate()).contains(date);
    }
}
