package co.brp.offers.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

@Entity
@Table(name = "oferta_participacion")
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oferta_id")
    private Offer offer;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_identificacion")
    private Offer.IdentificationType identificationType;

    @Column(name = "numero_identificacion")
    private String identificationNumber;

    @Column(name = "nombre")
    private String name;

    @Column(name = "contacto")
    private String contact;

    @Column(name = "porcentaje", precision = 7, scale = 4)
    private BigDecimal percentage;

    @OneToMany(mappedBy = "participation", cascade = CascadeType.PERSIST)
    private List<Payment> payments = new ArrayList<>();

    protected Participation() {
    }

    Participation(Offer offer, Offer.Bidder bidder) {
        if (bidder == null || bidder.identificationType() == null
                || bidder.identificationNumber() == null || bidder.identificationNumber().isBlank()
                || bidder.name() == null || bidder.name().isBlank()) {
            throw new Offer.InvalidOffer("Cada oferente requiere tipo, número de identificación y nombre.");
        }
        var share = bidder.percentage();
        if (share == null || share.signum() <= 0 || share.scale() > 4
                || share.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new Offer.InvalidOffer("La participación debe ser positiva, hasta 100 y con máximo cuatro decimales.");
        }
        if (bidder.payments() == null || bidder.payments().isEmpty() || bidder.payments().size() > 6) {
            throw new Offer.InvalidOffer("Cada oferente debe tener entre uno y seis pagos.");
        }
        this.offer = offer;
        identificationType = bidder.identificationType();
        identificationNumber = bidder.identificationNumber().strip();
        name = bidder.name();
        contact = bidder.contact();
        percentage = share.setScale(4);
        for (var terms : bidder.payments()) {
            payments.add(new Payment(this, terms));
        }
    }

    BigDecimal paymentTotal() {
        return payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void validateShare(BigDecimal offerTotal) {
        var expected = offerTotal.multiply(percentage).movePointLeft(2).stripTrailingZeros();
        if (expected.scale() > 2) {
            throw new Offer.InvalidOffer("El importe de participación no es exacto en dos decimales.");
        }
        if (paymentTotal().compareTo(expected) != 0) {
            throw new Offer.InvalidOffer("Los pagos del oferente deben coincidir con su participación.");
        }
    }

    public String identificationNumber() { return identificationNumber; }
    public Offer.IdentificationType identificationType() { return identificationType; }
    public String name() { return name; }
    public String contact() { return contact; }
    public BigDecimal percentage() { return percentage; }
    public List<Payment> payments() { return List.copyOf(payments); }
}
