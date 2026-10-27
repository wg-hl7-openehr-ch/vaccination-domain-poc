package ch.hl7.vacd.api.entity;

import java.util.Calendar;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "artefacts")
public class ArtefactEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String sessionId;

	private String patientId;

	@Enumerated(EnumType.STRING)
	private ArtefactEntityType artefactType;

	@Column(name = "artefact", columnDefinition = "TEXT")
	private String artefact;

	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	private Calendar lastUpdate;

	public ArtefactEntity() {
	}

	public ArtefactEntity(Long id, String sessionId, String patientId, String artefact, Calendar lastUpdate) {
		super();
		this.id = id;
		this.sessionId = sessionId;
		this.patientId = patientId;
		this.artefact = artefact;
		this.lastUpdate = lastUpdate;
	}

	public ArtefactEntity(String sessionId, String patientId, ArtefactEntityType artefactType, String artefact) {
		super();
		this.sessionId = sessionId;
		this.patientId = patientId;
		this.artefactType = artefactType;
		this.artefact = artefact;
	}

	public Long getId() {
		return id;
	}

	public ArtefactEntity setId(Long id) {
		this.id = id;
		return this;
	}

	public String getSessionId() {
		return sessionId;
	}

	public ArtefactEntity setSessionId(String sessionId) {
		this.sessionId = sessionId;
		return this;
	}

	public String getPatientId() {
		return patientId;
	}

	public ArtefactEntity setPatientId(String patientId) {
		this.patientId = patientId;
		return this;
	}

	public ArtefactEntityType getArtefactType() {
		return artefactType;
	}

	public ArtefactEntity setArtefactType(ArtefactEntityType artefactType) {
		this.artefactType = artefactType;
		return this;
	}

	public String getArtefact() {
		return artefact;
	}

	public ArtefactEntity setArtefact(String artefact) {
		this.artefact = artefact;
		return this;
	}

	public Calendar getLastUpdate() {
		return lastUpdate;
	}

	public ArtefactEntity setLastUpdate(Calendar lastUpdate) {
		this.lastUpdate = lastUpdate;
		return this;
	}

}
