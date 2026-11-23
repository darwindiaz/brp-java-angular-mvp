package co.brp.catalog.presentation;

import java.math.BigDecimal;
import java.util.List;

import co.brp.catalog.application.BrpCatalog;
import co.brp.catalog.domain.BrpAssetType;
import co.brp.catalog.domain.BrpVersion;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RegistrationCatalogController {

    private final BrpCatalog catalog;

    RegistrationCatalogController(BrpCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/offers/catalog")
    @PreAuthorize("isAuthenticated() and principal.canRegisterOffers()")
    List<RegistrationCatalogItem> list() {
        return catalog.available().stream().map(RegistrationCatalogItem::from).toList();
    }

    record RegistrationCatalogItem(String code, BrpAssetType assetType, String description,
                                   Location location, BigDecimal referencePrice) {
        private static RegistrationCatalogItem from(BrpVersion version) {
            return new RegistrationCatalogItem(version.getCode(), version.getAssetType(), version.getDescription(),
                    new Location(version.getDepartment(), version.getMunicipality()), version.getPrice());
        }
    }

    record Location(String department, String municipality) {
    }
}
