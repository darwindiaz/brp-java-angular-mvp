package co.brp.offers.presentation;

import java.util.List;

import co.brp.offers.application.OfferFollowUp;
import co.brp.offers.application.OfferFollowUpView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follow-up/offers")
@PreAuthorize("isAuthenticated() and principal.canFollowUp()")
class OfferFollowUpController {

    private final OfferFollowUp followUp;

    OfferFollowUpController(OfferFollowUp followUp) {
        this.followUp = followUp;
    }

    @GetMapping
    List<OfferFollowUpView> list() {
        return followUp.list();
    }

    @GetMapping("/{number}")
    OfferFollowUpView find(@PathVariable long number) {
        return followUp.find(number);
    }
}
