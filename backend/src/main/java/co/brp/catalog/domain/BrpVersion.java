package co.brp.catalog.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "brp_catalog")
public class BrpVersion {

    @Id
    private Long id;
    private String code;
    private LocalDate ingestionDate;

    @Enumerated(EnumType.STRING)
    private BrpStatus status;

    @Enumerated(EnumType.STRING)
    private BrpAssetType assetType;

    private String propertyRegistration;
    private String licensePlate;
    private String department;
    private String municipality;
    private String description;
    private BigDecimal price;
    private LocalDate priceAssignedOn;

    protected BrpVersion() {
    }

    public String getCode() {
        return code;
    }

    public LocalDate getIngestionDate() {
        return ingestionDate;
    }

    public BrpStatus getStatus() {
        return status;
    }

    public BrpAssetType getAssetType() {
        return assetType;
    }

    public String getPropertyRegistration() {
        return propertyRegistration;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getDepartment() {
        return department;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDate getPriceAssignedOn() {
        return priceAssignedOn;
    }
}
