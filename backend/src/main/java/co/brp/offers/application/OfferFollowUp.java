package co.brp.offers.application;

import java.util.List;
import java.util.NoSuchElementException;

import co.brp.identity.domain.IdentityUser;
import co.brp.offers.infrastructure.OfferFollowUpQueries;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OfferFollowUp {

    private final OfferFollowUpQueries queries;

    public OfferFollowUp(OfferFollowUpQueries queries) {
        this.queries = queries;
    }

    public List<OfferFollowUpView> list() {
        return queries.list(registrantScope());
    }

    public OfferFollowUpView find(long number) {
        return queries.find(number, registrantScope())
                .orElseThrow(() -> new NoSuchElementException("No existe la oferta."));
    }

    private Long registrantScope() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityUser user) || !user.canFollowUp()) {
            throw new AccessDeniedException("El seguimiento requiere el módulo habilitado.");
        }
        return switch (user.role()) {
            case RADICADOR -> user.id();
            case ANALISTA, JEFE, ADMINISTRADOR -> null;
        };
    }
}
