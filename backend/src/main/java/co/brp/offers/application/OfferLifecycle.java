package co.brp.offers.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import co.brp.identity.domain.IdentityUser;
import co.brp.offers.domain.Offer;
import co.brp.offers.domain.Offer.Action;
import co.brp.offers.domain.Offer.Status;
import co.brp.offers.infrastructure.OfferRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferLifecycle {

    private final OfferRepository offers;
    private final Clock clock;

    public OfferLifecycle(OfferRepository offers, Clock clock) {
        this.offers = offers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkItem> analysisQueue() {
        var actor = requireActor(true);
        return offers.findAnalysisQueue(actor.id(), Status.RADICADO, Status.EN_PROCESO).stream()
                .map(WorkItem::from).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkItem> approvalQueue() {
        requireActor(false);
        return offers.findByStatusOrderByRegistrationNumberDesc(Status.ESCALADO).stream()
                .map(WorkItem::from).toList();
    }

    @Transactional
    public WorkItem transition(long number, Action action, long version) {
        var actor = requireActor(switch (action) {
            case TAKE, ESCALATE, ANALYST_REJECT -> true;
            case APPROVE, CHIEF_REJECT, RETURN -> false;
        });
        var offer = offers.findByRegistrationNumber(number)
                .orElseThrow(() -> new NoSuchElementException("No existe la oferta."));
        offer.transition(action, actor.id(), version, clock.instant());

        // Hibernate actualiza la versión antes de construir la respuesta.
        offers.flush();
        return WorkItem.from(offer);
    }

    private IdentityUser requireActor(boolean analysis) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityUser user)
                || !(analysis ? user.canAnalyzeOffers() : user.canDecideOffers())) {
            throw new AccessDeniedException("La operación requiere el rol y módulo habilitados.");
        }
        return user;
    }

    public record WorkItem(OfferRegistration.Summary offer, Long analystId, long version, Instant modifiedAt,
                           Instant escalatedAt, Instant returnedAt, Instant closedAt) {

        static WorkItem from(Offer offer) {
            return new WorkItem(OfferRegistration.Summary.from(offer.brpCode(), offer),
                    offer.analystId(), offer.version(), offer.modifiedAt(),
                    offer.escalatedAt(), offer.returnedAt(), offer.closedAt());
        }
    }
}
