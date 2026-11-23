package co.brp.offers.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import co.brp.catalog.application.BrpCatalog;
import co.brp.catalog.domain.BrpStatus;
import co.brp.catalog.domain.BrpVersion;
import co.brp.identity.domain.IdentityUser;
import co.brp.offers.domain.Offer;
import co.brp.offers.infrastructure.OfferRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferRegistration {

    private final BrpCatalog catalog;
    private final OfferRepository repository;

    public OfferRegistration(BrpCatalog catalog, OfferRepository repository) {
        this.catalog = catalog;
        this.repository = repository;
    }

    @Transactional
    public Offer register(String brpCode, List<Offer.Bidder> bidders) {
        var user = requireRegistrant();
        var brp = availableBrp(brpCode);
        return repository.save(Offer.register(user.id(), brp.getId(), brp.getPrice(), Instant.now(), bidders));
    }

    @Transactional(readOnly = true)
    public Preview preview(String brpCode, BigDecimal total) {
        requireRegistrant();
        var price = availableBrp(brpCode).getPrice().setScale(2);
        var discount = Offer.calculateDiscount(price, total);
        return new Preview(price, total.setScale(2), discount,
                Offer.hasOverprice(price, total));
    }

    @Transactional(readOnly = true)
    public List<Summary> mine() {
        return repository.findByRegistrantIdOrderByRegistrationNumberDesc(requireRegistrant().id()).stream()
                .map(offer -> Summary.from(offer.brpCode(), offer)).toList();
    }

    @Transactional(readOnly = true)
    public Detail mine(long number) {
        var offer = repository.findByRegistrantIdAndRegistrationNumber(requireRegistrant().id(), number)
                .orElseThrow(() -> new NoSuchElementException("No existe la oferta."));
        var bidders = offer.participations().stream().map(participation -> new BidderView(
                participation.identificationType(), participation.identificationNumber(),
                participation.name(), participation.contact(), participation.percentage(),
                participation.payments().stream().map(payment -> new PaymentView(
                        payment.method(), payment.date(), payment.amount(), payment.lender())).toList())).toList();
        return new Detail(Summary.from(offer.brpCode(), offer), bidders);
    }

    private IdentityUser requireRegistrant() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityUser user)
                || !user.canRegisterOffers()) {
            throw new AccessDeniedException("La radicación requiere el rol y módulo habilitados.");
        }
        return user;
    }

    private BrpVersion availableBrp(String brpCode) {
        var brp = catalog.currentByCode(brpCode)
                .orElseThrow(() -> new NoSuchElementException("No existe el BRP."));
        var price = brp.getPrice();
        if (brp.getStatus() != BrpStatus.DISPONIBLE || !Offer.supportsMoney(price)) {
            throw new BrpUnavailable();
        }
        return brp;
    }

    public static final class BrpUnavailable extends IllegalStateException {
        BrpUnavailable() {
            super("El BRP vigente no está disponible con un precio válido.");
        }
    }

    public record Preview(BigDecimal referencePrice, BigDecimal total, BigDecimal discount, boolean overprice) {
    }

    public record Summary(long registrationNumber, String brpCode, BigDecimal referencePrice, BigDecimal total,
                          BigDecimal discount, boolean overprice, Offer.Status status, Instant createdAt) {
        public static Summary from(String code, Offer offer) {
            return new Summary(offer.registrationNumber(), code, offer.referencePrice(), offer.total(),
                    offer.discount(), offer.hasOverprice(), offer.status(), offer.createdAt());
        }
    }

    public record Detail(long registrationNumber, String brpCode, BigDecimal referencePrice, BigDecimal total,
                         BigDecimal discount, boolean overprice, Offer.Status status, Instant createdAt,
                         List<BidderView> bidders) {
        Detail(Summary offer, List<BidderView> bidders) {
            this(offer.registrationNumber(), offer.brpCode(), offer.referencePrice(), offer.total(),
                    offer.discount(), offer.overprice(), offer.status(), offer.createdAt(), bidders);
        }
    }

    public record BidderView(Offer.IdentificationType identificationType, String identificationNumber,
                             String name, String contact, BigDecimal percentage, List<PaymentView> payments) {
    }

    public record PaymentView(Offer.PaymentMethod method, LocalDate date, BigDecimal amount, String lender) {
    }
}
