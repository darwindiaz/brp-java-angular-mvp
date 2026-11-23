package co.brp.offers.presentation;

import java.util.List;

import co.brp.offers.application.OfferLifecycle;
import co.brp.offers.application.OfferLifecycle.WorkItem;
import co.brp.offers.domain.Offer.Action;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offers")
class OfferLifecycleController {

    private static final String ANALYSIS = "hasRole('ANALISTA') and hasAuthority('MODULE_ANALISIS')";
    private static final String APPROVAL = "hasRole('JEFE') and hasAuthority('MODULE_APROBACION')";

    private final OfferLifecycle lifecycle;

    OfferLifecycleController(OfferLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @GetMapping("/analysis")
    @PreAuthorize(ANALYSIS)
    List<WorkItem> analysisQueue() {
        return lifecycle.analysisQueue();
    }

    @GetMapping("/approval")
    @PreAuthorize(APPROVAL)
    List<WorkItem> approvalQueue() {
        return lifecycle.approvalQueue();
    }

    @PostMapping("/analysis/{number}/{action:take|escalate|reject}")
    @PreAuthorize(ANALYSIS)
    WorkItem analyze(@PathVariable long number, @PathVariable String action, @Valid @RequestBody Command command) {
        var transition = switch (action) {
            case "take" -> Action.TAKE;
            case "escalate" -> Action.ESCALATE;
            case "reject" -> Action.ANALYST_REJECT;
            default -> throw new IllegalArgumentException("Acción desconocida.");
        };
        return lifecycle.transition(number, transition, command.version());
    }

    @PostMapping("/approval/{number}/{action:approve|reject|return}")
    @PreAuthorize(APPROVAL)
    WorkItem decide(@PathVariable long number, @PathVariable String action, @Valid @RequestBody Command command) {
        var transition = switch (action) {
            case "approve" -> Action.APPROVE;
            case "reject" -> Action.CHIEF_REJECT;
            case "return" -> Action.RETURN;
            default -> throw new IllegalArgumentException("Acción desconocida.");
        };
        return lifecycle.transition(number, transition, command.version());
    }

    record Command(@NotNull @PositiveOrZero Long version) implements OfferController.StrictRequest {
    }
}
