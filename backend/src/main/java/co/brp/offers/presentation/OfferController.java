package co.brp.offers.presentation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import co.brp.offers.application.OfferRegistration;
import co.brp.offers.domain.Offer;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offers")
@PreAuthorize("hasRole('RADICADOR') and hasAuthority('MODULE_RADICACION')")
class OfferController {

    private final OfferRegistration registration;

    OfferController(OfferRegistration registration) {
        this.registration = registration;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OfferRegistration.Summary register(@Valid @RequestBody RegisterRequest request) {
        return OfferRegistration.Summary.from(request.brpCode(),
                registration.register(request.brpCode(), request.bidders().stream().map(BidderRequest::toDomain).toList()));
    }

    @PostMapping("/discount-preview")
    OfferRegistration.Preview preview(@Valid @RequestBody PreviewRequest request) {
        return registration.preview(request.brpCode(), request.total());
    }

    @GetMapping("/mine")
    List<OfferRegistration.Summary> mine() {
        return registration.mine();
    }

    @GetMapping("/mine/{number}")
    OfferRegistration.Detail mine(@PathVariable long number) {
        return registration.mine(number);
    }

    interface StrictRequest {
        @JsonAnySetter
        default void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("La solicitud contiene un campo no admitido.");
        }
    }

    record RegisterRequest(@NotBlank @Size(max = 120) String brpCode,
                           @NotNull @Size(min = 1, max = 5) List<@NotNull @Valid BidderRequest> bidders)
            implements StrictRequest {
    }

    record PreviewRequest(@NotBlank @Size(max = 120) String brpCode,
                          @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal total)
            implements StrictRequest {
    }

    record BidderRequest(@NotBlank @Pattern(regexp = "CC|NIT") String identificationType,
                         @NotBlank @Size(max = 80) String identificationNumber,
                         @NotBlank @Size(max = 200) String name,
                         @Size(max = 254) String contact,
                         @NotNull @Positive @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal percentage,
                         @NotNull @Size(min = 1, max = 6) List<@NotNull @Valid PaymentRequest> payments)
            implements StrictRequest {
        Offer.Bidder toDomain() {
            return new Offer.Bidder(Offer.IdentificationType.valueOf(identificationType),
                    identificationNumber, name, contact, percentage, payments.stream().map(PaymentRequest::toDomain).toList());
        }
    }

    record PaymentRequest(@NotBlank @Pattern(regexp = "CONTADO|PRESTAMO") String method,
                          @NotNull LocalDate date,
                          @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
                          @Size(max = 200) String lender) implements StrictRequest {
        Offer.PaymentTerms toDomain() {
            return new Offer.PaymentTerms(Offer.PaymentMethod.valueOf(method), date, amount, lender);
        }
    }
}
