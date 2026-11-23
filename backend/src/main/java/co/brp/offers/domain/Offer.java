package co.brp.offers.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import co.brp.catalog.domain.BrpVersion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "oferta")
public class Offer {

    private static final BigDecimal PERCENT = new BigDecimal("100.0000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Generated
    @Column(name = "numero_radicacion", insertable = false, updatable = false)
    private Long registrationNumber;

    @Column(name = "radicador_id", nullable = false, updatable = false)
    private long registrantId;

    @Column(name = "brp_version_id", nullable = false, updatable = false)
    private long brpVersionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brp_version_id", insertable = false, updatable = false)
    private BrpVersion brpVersion;

    @Column(name = "precio_referencia", precision = 19, scale = 2, updatable = false)
    private BigDecimal referencePrice;

    @Column(name = "valor_total", precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "descuento", precision = 25, scale = 4)
    private BigDecimal discount;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private Status status;

    @Column(name = "creado_en", updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version")
    private long version;

    @OneToMany(mappedBy = "offer", cascade = CascadeType.PERSIST)
    private List<Participation> participations = new ArrayList<>();

    protected Offer() {
    }

    public static Offer register(long registrantId, long brpVersionId, BigDecimal referencePrice,
                                 Instant createdAt, List<Bidder> bidders) {
        var offer = new Offer();
        offer.registrantId = registrantId;
        offer.brpVersionId = brpVersionId;
        offer.referencePrice = positiveMoney(referencePrice);
        offer.createdAt = Objects.requireNonNull(createdAt);
        offer.status = Status.RADICADO;

        if (bidders == null || bidders.isEmpty() || bidders.size() > 5) {
            throw new InvalidOffer("La oferta debe tener entre uno y cinco oferentes.");
        }
        var documents = new HashSet<String>();
        var percentage = BigDecimal.ZERO;
        var amount = BigDecimal.ZERO;
        for (var bidder : bidders) {
            var participation = new Participation(offer, bidder);
            if (!documents.add(participation.identificationNumber())) {
                throw new InvalidOffer("Hay documentos duplicados en la oferta.");
            }
            offer.participations.add(participation);
            percentage = percentage.add(participation.percentage());
            amount = amount.add(participation.paymentTotal());
        }
        if (percentage.compareTo(PERCENT) != 0) {
            throw new InvalidOffer("Las participaciones deben sumar 100.0000 %.");
        }
        offer.total = positiveMoney(amount);
        for (var participation : offer.participations) {
            participation.validateShare(offer.total);
        }
        offer.discount = calculateDiscount(offer.referencePrice, offer.total);
        return offer;
    }

    public static BigDecimal calculateDiscount(BigDecimal referencePrice, BigDecimal total) {
        var price = positiveMoney(referencePrice);
        return price.subtract(positiveMoney(total)).multiply(PERCENT)
                .divide(price, 4, RoundingMode.HALF_UP);
    }

    static BigDecimal positiveMoney(BigDecimal amount) {
        if (!supportsMoney(amount)) {
            throw new InvalidOffer("El importe debe ser positivo y ajustarse a NUMERIC(19,2).");
        }
        return amount.setScale(2);
    }

    public static boolean supportsMoney(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.scale() <= 2
                && (long) amount.precision() - amount.scale() <= 17;
    }

    public boolean hasOverprice() {
        return hasOverprice(referencePrice, total);
    }

    public static boolean hasOverprice(BigDecimal referencePrice, BigDecimal total) {
        return total.compareTo(referencePrice) > 0;
    }

    public Long id() { return id; }
    public Long registrationNumber() { return registrationNumber; }
    public long brpVersionId() { return brpVersionId; }
    public String brpCode() { return brpVersion.getCode(); }
    public long registrantId() { return registrantId; }
    public BigDecimal referencePrice() { return referencePrice; }
    public BigDecimal total() { return total; }
    public BigDecimal discount() { return discount; }
    public Status status() { return status; }
    public Instant createdAt() { return createdAt; }
    public List<Participation> participations() { return List.copyOf(participations); }

    public enum Status {
        RADICADO, EN_PROCESO, ESCALADO, APROBADO, RECHAZADO, DESISTIDO
    }

    public enum IdentificationType { CC, NIT }
    public enum PaymentMethod { CONTADO, PRESTAMO }

    public static final class InvalidOffer extends IllegalArgumentException {
        InvalidOffer(String message) {
            super(message);
        }
    }

    public record Bidder(IdentificationType identificationType, String identificationNumber,
                         String name, String contact, BigDecimal percentage, List<PaymentTerms> payments) {
    }

    public record PaymentTerms(PaymentMethod method, LocalDate date, BigDecimal amount, String lender) {
    }
}
