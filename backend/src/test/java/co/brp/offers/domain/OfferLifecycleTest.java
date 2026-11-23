package co.brp.offers.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import co.brp.offers.domain.Offer.Action;
import co.brp.offers.domain.Offer.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferLifecycleTest {

    private static final Instant START = Instant.parse("2026-11-23T00:00:00Z");

    @Test
    void preservesAssignmentAndUpdatesDatesAcrossRepeatedReturns() {
        var offer = registered();
        offer.transition(Action.TAKE, 2, 0, START.plusSeconds(1));
        offer.transition(Action.ESCALATE, 2, 0, START.plusSeconds(2));
        offer.transition(Action.RETURN, 3, 0, START.plusSeconds(3));
        offer.transition(Action.ESCALATE, 2, 0, START.plusSeconds(4));
        offer.transition(Action.RETURN, 4, 0, START.plusSeconds(5));

        assertThat(offer.status()).isEqualTo(Status.EN_PROCESO);
        assertThat(offer.analystId()).isEqualTo(2L);
        assertThat(offer.createdAt()).isEqualTo(START);
        assertThat(offer.escalatedAt()).isEqualTo(START.plusSeconds(4));
        assertThat(offer.returnedAt()).isEqualTo(START.plusSeconds(5));
        assertThat(offer.modifiedAt()).isEqualTo(offer.returnedAt());
        assertThat(offer.closedAt()).isNull();
    }

    @Test
    void rejectsInvalidActionsBeforeChangingStateOrDates() {
        var offer = registered();
        assertThatThrownBy(() -> offer.transition(Action.TAKE, 1, 0, START))
                .isInstanceOf(Offer.ForbiddenAction.class);
        assertThatThrownBy(() -> offer.transition(Action.TAKE, 2, 1, START))
                .isInstanceOf(Offer.TransitionConflict.class);
        assertThatThrownBy(() -> offer.transition(Action.APPROVE, 3, 0, START))
                .isInstanceOf(Offer.TransitionConflict.class);
        assertThat(offer.status()).isEqualTo(Status.RADICADO);
        assertThat(offer.analystId()).isNull();
        assertThat(offer.modifiedAt()).isNull();
    }

    private Offer registered() {
        var payment = new Offer.PaymentTerms(Offer.PaymentMethod.CONTADO, LocalDate.of(2026, 11, 23),
                BigDecimal.ONE, null);
        return Offer.register(1, 1, BigDecimal.ONE, START, List.of(
                new Offer.Bidder(Offer.IdentificationType.CC, "1", "Oferente", null,
                        BigDecimal.valueOf(100), List.of(payment))));
    }
}
