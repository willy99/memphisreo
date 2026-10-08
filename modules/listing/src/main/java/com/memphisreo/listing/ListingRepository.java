package com.memphisreo.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    Optional<Listing> findFirstByPropertyIdAndStatusNotInOrderByCreatedAtDesc(UUID propertyId,
                                                                             Collection<Listing.Status> statuses);

    List<Listing> findByPropertyIdInAndStatusNotIn(Collection<UUID> propertyIds, Collection<Listing.Status> statuses);
}
