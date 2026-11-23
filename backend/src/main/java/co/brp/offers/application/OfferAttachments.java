package co.brp.offers.application;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import co.brp.identity.domain.IdentityUser;
import co.brp.offers.domain.Offer;
import co.brp.offers.domain.OfferAttachment;
import co.brp.offers.infrastructure.LocalAttachmentStorage;
import co.brp.offers.infrastructure.OfferAttachmentRepository;
import co.brp.offers.infrastructure.OfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OfferAttachments {

    private static final Logger LOG = LoggerFactory.getLogger(OfferAttachments.class);

    private final AttachmentValidation validation;
    private final OfferRepository offers;
    private final OfferAttachmentRepository attachments;
    private final LocalAttachmentStorage storage;
    private final Clock clock;
    private final TransactionTemplate uploadTransaction;

    public OfferAttachments(AttachmentValidation validation, OfferRepository offers,
                            OfferAttachmentRepository attachments, LocalAttachmentStorage storage,
                            Clock clock, PlatformTransactionManager transactionManager) {
        this.validation = validation;
        this.offers = offers;
        this.attachments = attachments;
        this.storage = storage;
        this.clock = clock;
        uploadTransaction = new TransactionTemplate(transactionManager);
        uploadTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<Metadata> upload(long number, List<MultipartFile> files) {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un archivo.");
        }
        var validated = files.stream().map(validation::validate).toList();
        try {
            return uploadTransaction.execute(status -> storeAttachments(number, validated));
        } catch (DataAccessException | TransactionException failure) {
            LOG.error("Falló la persistencia de adjuntos ({})", failure.getClass().getSimpleName());
            throw new PersistenceFailure();
        }
    }

    private List<Metadata> storeAttachments(long number, List<AttachmentValidation.ValidatedFile> validated) {
        var offer = authorizedOffer(number);
        var created = new ArrayList<UUID>();
        registerCompensation(created);
        var rows = new ArrayList<OfferAttachment>();
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        for (var file : validated) {
            UUID id = UUID.randomUUID();
            long size = file.file().getSize();
            try (var input = file.file().getInputStream()) {
                storage.write(id, input, size);
                created.add(id);
            } catch (IOException e) {
                throw new StorageFailure(e);
            }
            rows.add(new OfferAttachment(id, offer.id(), file.filename(), file.contentType(), size, now));
        }
        return attachments.saveAllAndFlush(rows).stream().map(Metadata::from).toList();
    }

    private void registerCompensation(List<UUID> created) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    for (var id : created) {
                        try {
                            storage.delete(id);
                        } catch (IOException | RuntimeException failure) {
                            storage.reportCleanupFailure(id);
                        }
                    }
                } else if (status == STATUS_UNKNOWN && !created.isEmpty()) {
                    LOG.error("Resultado transaccional de adjuntos indeterminado. Conciliar UUID con PostgreSQL: {}", created);
                }
            }
        });
    }

    @Transactional(readOnly = true)
    public List<Metadata> list(long number) {
        var offer = authorizedOffer(number);
        return attachments.findAllByOfferIdOrderByUploadedAtAscIdAsc(offer.id()).stream()
                .map(Metadata::from).toList();
    }

    @Transactional(readOnly = true)
    public Download download(long number, UUID id) {
        var offer = authorizedOffer(number);
        var attachment = attachments.findByOfferIdAndId(offer.id(), id)
                .orElseThrow(() -> new NoSuchElementException("No existe el adjunto."));
        try {
            return new Download(Metadata.from(attachment), storage.open(attachment.getId()));
        } catch (IOException e) {
            throw new StorageFailure(e);
        }
    }

    private Offer authorizedOffer(long number) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityUser user) || !user.canAccessOfferAttachments()) {
            throw new AccessDeniedException("La operación requiere un módulo habilitado.");
        }
        var match = user.role() == IdentityUser.Role.RADICADOR
                ? offers.findByRegistrantIdAndRegistrationNumber(user.id(), number)
                : offers.findByRegistrationNumber(number);
        var offer = match.orElseThrow(() -> new NoSuchElementException("No existe la oferta."));
        boolean permitted = switch (user.role()) {
            case RADICADOR, ADMINISTRADOR -> true;
            case ANALISTA -> Objects.equals(offer.analystId(), user.id());
            case JEFE -> offer.status() == Offer.Status.ESCALADO;
        };
        if (!permitted) {
            throw new Offer.ForbiddenAction();
        }
        return offer;
    }

    public record Metadata(UUID id, String filename, String contentType, long size, Instant uploadedAt) {
        static Metadata from(OfferAttachment attachment) {
            return new Metadata(attachment.getId(), attachment.filename(), attachment.contentType(),
                    attachment.size(), attachment.uploadedAt());
        }
    }

    public record Download(Metadata metadata, InputStream content) {
    }

    public static final class TooLarge extends RuntimeException {
    }

    public static final class PersistenceFailure extends RuntimeException {
    }

    public static final class StorageFailure extends RuntimeException {
        StorageFailure(IOException cause) {
            super("No se pudo acceder al almacenamiento de adjuntos.", cause);
        }
    }
}
