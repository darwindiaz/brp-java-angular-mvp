package co.brp.offers.infrastructure;

import java.util.List;
import java.util.Optional;

import co.brp.offers.domain.Offer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    @EntityGraph(attributePaths = "brpVersion")
    List<Offer> findByRegistrantIdOrderByRegistrationNumberDesc(long registrantId);

    @EntityGraph(attributePaths = "brpVersion")
    Optional<Offer> findByRegistrantIdAndRegistrationNumber(long registrantId, long registrationNumber);
}
