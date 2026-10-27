package ch.hl7.vacd.api.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.hl7.vacd.api.entity.ArtefactEntity;
import ch.hl7.vacd.api.entity.ArtefactEntityType;

@Repository
public interface ArtefactRepository extends JpaRepository<ArtefactEntity, Long> {

	List<ArtefactEntity> findByPatientId(String idPart);

	Optional<ArtefactEntity> findFirstByPatientIdAndArtefactTypeOrderByLastUpdateDesc(String patientId,
			ArtefactEntityType artefactType);

	List<ArtefactEntity> findByPatientIdAndSessionId(String idPart, String sessionId);

}
