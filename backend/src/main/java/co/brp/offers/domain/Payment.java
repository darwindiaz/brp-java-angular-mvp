package co.brp.offers.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "oferta_pago")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participacion_id")
    private Participation participation;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad")
    private Offer.PaymentMethod method;

    @Column(name = "fecha")
    private LocalDate date;

    @Column(name = "valor", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "financiadora")
    private String lender;

    protected Payment() {
    }

    Payment(Participation participation, Offer.PaymentTerms terms) {
        if (terms == null || terms.method() == null || terms.date() == null) {
            throw new Offer.InvalidOffer("Cada pago requiere modalidad y fecha.");
        }
        if (terms.method() == Offer.PaymentMethod.CONTADO && terms.lender() != null) {
            throw new Offer.InvalidOffer("La financiadora solo aplica a préstamos.");
        }
        this.participation = participation;
        method = terms.method();
        date = terms.date();
        amount = Offer.positiveMoney(terms.amount());
        lender = terms.lender();
    }

    public BigDecimal amount() { return amount; }
    public Offer.PaymentMethod method() { return method; }
    public LocalDate date() { return date; }
    public String lender() { return lender; }
}
