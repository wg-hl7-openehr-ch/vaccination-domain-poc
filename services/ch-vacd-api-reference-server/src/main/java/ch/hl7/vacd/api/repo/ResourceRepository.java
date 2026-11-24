package ch.hl7.vacd.api.repo;

import ch.hl7.vacd.api.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<ResourceEntity, Long> {
    List<ResourceEntity> findByResourceType(String resourceType);
    List<ResourceEntity> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    /**
     * Finds the resources of a type that carry the given identifier. The
     * identifiers of the found resources are fetched with them.
     */
    @Query("select distinct r from ResourceEntity r join fetch r.identifiers join r.identifiers i "
            + "where r.resourceType = :resourceType and i.idSystem = :idSystem and i.idValue = :idValue")
    List<ResourceEntity> findByResourceTypeAndIdentifier(@Param("resourceType") String resourceType,
            @Param("idSystem") String idSystem, @Param("idValue") String idValue);
}
