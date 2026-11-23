package co.brp.catalog.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import co.brp.catalog.domain.BrpAssetType;
import co.brp.catalog.domain.BrpStatus;
import co.brp.catalog.domain.BrpVersion;
import co.brp.identity.domain.IdentityUser.Role;
import org.springframework.stereotype.Service;

@Service
public class FollowUpCatalog {

    private final BrpCatalog catalog;

    public FollowUpCatalog(BrpCatalog catalog) {
        this.catalog = catalog;
    }

    public List<CatalogItem> list(Role role) {
        var versions = switch (role) {
            case RADICADOR -> catalog.available();
            case ANALISTA, JEFE, ADMINISTRADOR -> catalog.current();
        };
        return versions.stream().map(CatalogItem::from).toList();
    }

    public Freshness freshness() {
        return new Freshness(catalog.latestIngestionDate().orElse(null));
    }

    public record CatalogItem(String code, BrpAssetType assetType, String description, Location location,
                              BrpStatus status, BigDecimal referencePrice, LocalDate ingestionDate) {

        private static CatalogItem from(BrpVersion version) {
            return new CatalogItem(version.getCode(), version.getAssetType(), version.getDescription(),
                    new Location(version.getDepartment(), version.getMunicipality()), version.getStatus(),
                    version.getPrice(), version.getIngestionDate());
        }
    }

    public record Location(String department, String municipality) {
    }

    public record Freshness(LocalDate latestIngestionDate) {
    }
}
