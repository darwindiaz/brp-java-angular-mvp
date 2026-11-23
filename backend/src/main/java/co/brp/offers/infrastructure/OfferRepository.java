package co.brp.offers.infrastructure;

import java.util.List;
import java.util.Optional;

import co.brp.offers.domain.Offer;
import co.brp.offers.domain.Offer.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    @EntityGraph(attributePaths = "brpVersion")
    List<Offer> findByRegistrantIdOrderByRegistrationNumberDesc(long registrantId);

    @EntityGraph(attributePaths = "brpVersion")
    Optional<Offer> findByRegistrantIdAndRegistrationNumber(long registrantId, long registrationNumber);

    @EntityGraph(attributePaths = "brpVersion")
    Optional<Offer> findByRegistrationNumber(long registrationNumber);

    @EntityGraph(attributePaths = "brpVersion")
    List<Offer> findByStatusOrderByRegistrationNumberDesc(Status status);

    @EntityGraph(attributePaths = "brpVersion")
    @Query("""
            SELECT offer FROM Offer offer
            WHERE (offer.status = :registered AND offer.registrantId <> :analystId)
               OR (offer.status = :inProgress AND offer.analystId = :analystId)
            ORDER BY offer.registrationNumber DESC
            """)
    List<Offer> findAnalysisQueue(long analystId, Status registered, Status inProgress);
}
