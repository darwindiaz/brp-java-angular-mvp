package co.brp.offers.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static co.brp.offers.domain.Offer.IdentificationType.CC;
import static co.brp.offers.domain.Offer.IdentificationType.NIT;
import static co.brp.offers.domain.Offer.PaymentMethod.CONTADO;
import static co.brp.offers.domain.Offer.PaymentMethod.PRESTAMO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void acceptsBidderAndPaymentBoundaries(int count) {
        var bidders = IntStream.range(0, count)
                .mapToObj(i -> bidder("doc-" + i, count == 1 ? "100" : "20",
                        Collections.nCopies(count == 1 ? 1 : 6, payment(CONTADO, "1", null))))
                .toList();
        var offer = register(bidders);
        assertThat(offer.status()).isEqualTo(Offer.Status.RADICADO);
        assertThat(offer.total()).isEqualByComparingTo(count == 1 ? "1.00" : "30.00");
        assertThat(offer.participations()).hasSize(count);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void rejectsInvalidBidderCounts(int count) {
        assertThatThrownBy(() -> register(Collections.nCopies(count, bidder("A", "100", "1"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cinco");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void rejectsInvalidPaymentCounts(int count) {
        var payments = Collections.nCopies(count, payment(CONTADO, "1", null));
        assertThatThrownBy(() -> register(List.of(bidder("A", "100", payments))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("seis");
    }

    @Test
    void rejectsDuplicateDocumentsEvenWithDifferentTypes() {
        var duplicate = new Offer.Bidder(NIT, " A ", "Nombre", null, new BigDecimal("50"),
                List.of(payment(CONTADO, "1", null)));
        assertThatThrownBy(() -> register(List.of(bidder("A", "50", "1"), duplicate)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicados");
    }

    @ParameterizedTest
    @CsvSource({
            "49, 50, 1, 1, sumar",
            "50, 50, 15, 5, coincidir",
            "50, 50, 0.01, 0.02, exacto"
    })
    void rejectsInvalidTotals(String firstShare, String secondShare, String firstAmount,
                              String secondAmount, String message) {
        assertThatThrownBy(() -> register(List.of(bidder("A", firstShare, firstAmount),
                bidder("B", secondShare, secondAmount))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    @Test
    void rejectsCashWithLender() {
        var cash = payment(CONTADO, "1", "Entidad");
        assertThatThrownBy(() -> register(List.of(bidder("A", "100", List.of(cash)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("financiadora");
    }

    @Test
    void acceptsLoansWithAndWithoutLender() {
        var payments = List.of(payment(PRESTAMO, "1.01", "Entidad"), payment(PRESTAMO, "1.99", null));
        assertThat(register(List.of(bidder("A", "100", payments))).total()).isEqualByComparingTo("3.00");
    }

    @ParameterizedTest
    @CsvSource({"75.00,25.0000", "100,0.0000", "101,-1.0000"})
    void calculatesDiscount(String total, String expected) {
        assertThat(Offer.calculateDiscount(new BigDecimal("100"), new BigDecimal(total)))
                .isEqualTo(new BigDecimal(expected));
    }

    @Test
    void flagsOverpriceWhenStoredDiscountRoundsToZero() {
        var offer = Offer.register(1, 2, new BigDecimal("10000000"), Instant.EPOCH,
                List.of(bidder("A", "100", "10000000.01")));
        assertThat(offer.discount()).isEqualByComparingTo("0.0000");
        assertThat(offer.hasOverprice()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "1.001"})
    void rejectsInvalidReferencePrice(String value) {
        var price = value == null ? null : new BigDecimal(value);
        assertThatThrownBy(() -> Offer.calculateDiscount(price, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"100, 1.000, NUMERIC", "100.00000, 1, decimales", "100, 0, NUMERIC",
                "100, 100000000000000000, NUMERIC"})
    void rejectsInvalidPrecisionOrAmount(String share, String amount, String message) {
        assertThatThrownBy(() -> register(List.of(bidder("A", share, amount))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    private Offer register(List<Offer.Bidder> bidders) {
        return Offer.register(1, 2, new BigDecimal("100"), Instant.EPOCH, bidders);
    }

    private Offer.Bidder bidder(String document, String share, String amount) {
        return bidder(document, share, List.of(payment(CONTADO, amount, null)));
    }

    private Offer.Bidder bidder(String document, String share, List<Offer.PaymentTerms> payments) {
        return new Offer.Bidder(CC, document, "Nombre", null, new BigDecimal(share), payments);
    }

    private Offer.PaymentTerms payment(Offer.PaymentMethod method, String amount, String lender) {
        return new Offer.PaymentTerms(method, LocalDate.of(2026, 11, 22), new BigDecimal(amount), lender);
    }
}
