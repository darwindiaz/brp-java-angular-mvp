package co.brp.offers.application;

import java.math.BigDecimal;
import java.time.Instant;

import co.brp.offers.domain.Offer.Status;

public record OfferFollowUpView(long registrationNumber, String brpCode, BigDecimal total,
                               BigDecimal discount, Status status, AssignedAnalyst analyst,
                               Instant createdAt, Instant modifiedAt, Instant escalatedAt,
                               Instant returnedAt, Instant closedAt) {

    public record AssignedAnalyst(long id, String name) {
    }
}
