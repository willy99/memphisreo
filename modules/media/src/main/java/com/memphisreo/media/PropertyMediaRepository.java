package com.memphisreo.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PropertyMediaRepository extends JpaRepository<PropertyMedia, UUID> {

    List<PropertyMedia> findByPropertyIdOrderByKindAscPositionAsc(UUID propertyId);

    List<PropertyMedia> findByPropertyIdAndKindOrderByPositionAsc(UUID propertyId, PropertyMedia.Kind kind);

    long countByPropertyIdAndKindIn(UUID propertyId, Collection<PropertyMedia.Kind> kinds);

    List<PropertyMedia> findByPropertyIdInAndCoverTrue(Collection<UUID> propertyIds);

    List<PropertyMedia> findByPropertyIdInAndKind(Collection<UUID> propertyIds, PropertyMedia.Kind kind);

    boolean existsByPropertyIdAndCoverTrue(UUID propertyId);

    @Query("select coalesce(max(m.position), -1) from PropertyMedia m where m.propertyId = :propertyId and m.kind = :kind")
    int maxPosition(@Param("propertyId") UUID propertyId, @Param("kind") PropertyMedia.Kind kind);

    /**
     * Серіалізує зміни медіа одного об'єкта до кінця транзакції (advisory lock
     * Postgres): паралельні завантаження не стануть обидва "першим фото".
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS lock", nativeQuery = true)
    Integer lockProperty(@Param("key") String key);
}
