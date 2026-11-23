package co.brp.catalog.presentation;

import java.util.List;

import co.brp.catalog.application.FollowUpCatalog;
import co.brp.catalog.application.FollowUpCatalog.CatalogItem;
import co.brp.catalog.application.FollowUpCatalog.Freshness;
import co.brp.identity.domain.IdentityUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follow-up/catalog")
@PreAuthorize("isAuthenticated() and principal.canFollowUp()")
class FollowUpCatalogController {

    private final FollowUpCatalog catalog;

    FollowUpCatalogController(FollowUpCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<CatalogItem> list(@AuthenticationPrincipal IdentityUser user) {
        return catalog.list(user.role());
    }

    @GetMapping("/freshness")
    Freshness freshness() {
        return catalog.freshness();
    }
}
