package co.brp.offers.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import co.brp.offers.domain.OfferAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferAttachmentRepository extends JpaRepository<OfferAttachment, UUID> {

    List<OfferAttachment> findAllByOfferIdOrderByUploadedAtAscIdAsc(long offerId);

    Optional<OfferAttachment> findByOfferIdAndId(long offerId, UUID id);
}
