package com.memphisreo.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PropertyMediaRepository extends JpaRepository<PropertyMedia, UUID> {

    List<PropertyMedia> findByPropertyIdOrderByKindAscPositionAsc(UUID propertyId);

    List<PropertyMedia> findByPropertyIdAndKindOrderByPositionAsc(UUID propertyId, PropertyMedia.Kind kind);

    long countByPropertyIdAndKindIn(UUID propertyId, Collection<PropertyMedia.Kind> kinds);

    List<PropertyMedia> findByPropertyIdInAndCoverTrue(Collection<UUID> propertyIds);

    List<PropertyMedia> findByPropertyIdInAndKind(Collection<UUID> propertyIds, PropertyMedia.Kind kind);
}
