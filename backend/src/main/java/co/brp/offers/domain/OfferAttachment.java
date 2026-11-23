package co.brp.offers.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "oferta_adjunto")
public class OfferAttachment implements Persistable<UUID> {

    // El UUID identifica el adjunto y es su clave de almacenamiento relativa.
    @Id
    private UUID id;

    @Column(name = "oferta_id")
    private long offerId;

    @Column(name = "nombre_archivo")
    private String filename;

    @Column(name = "tipo_contenido")
    private String contentType;

    @Column(name = "tamano_bytes")
    private long size;

    @Column(name = "cargado_en")
    private Instant uploadedAt;

    @Transient
    private boolean persisted;

    protected OfferAttachment() {
    }

    public OfferAttachment(UUID id, long offerId, String filename, String contentType, long size, Instant uploadedAt) {
        this.id = id;
        this.offerId = offerId;
        this.filename = filename;
        this.contentType = contentType;
        this.size = size;
        this.uploadedAt = uploadedAt;
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return !persisted; }

    @PostLoad
    @PostPersist
    private void markPersisted() {
        persisted = true;
    }

    public String filename() { return filename; }
    public String contentType() { return contentType; }
    public long size() { return size; }
    public Instant uploadedAt() { return uploadedAt; }
}
