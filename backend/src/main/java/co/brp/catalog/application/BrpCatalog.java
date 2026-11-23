package co.brp.catalog.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import co.brp.catalog.domain.BrpStatus;
import co.brp.catalog.domain.BrpVersion;
import co.brp.catalog.infrastructure.BrpCatalogRepository;
import org.springframework.stereotype.Service;

@Service
public class BrpCatalog {

    private final BrpCatalogRepository repository;

    public BrpCatalog(BrpCatalogRepository repository) {
        this.repository = repository;
    }

    public List<BrpVersion> current() {
        return repository.findCurrent();
    }

    public List<BrpVersion> available() {
        return repository.findCurrentByStatus(BrpStatus.DISPONIBLE);
    }

    public Optional<BrpVersion> currentByCode(String code) {
        return repository.findCurrentByCode(code);
    }

    public Optional<LocalDate> latestIngestionDate() {
        return repository.findLatestIngestionDate();
    }
}
