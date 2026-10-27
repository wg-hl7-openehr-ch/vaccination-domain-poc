package ch.vaccination.domain.audittrace.domain;

import java.util.Calendar;
import java.util.Date;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * 
 */
@Entity
public class AuditTraceLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@PreUpdate
	public void onUpdate() {
		this.lastUpdate = Calendar.getInstance();
	}

	@Column(name = "fhir_id")
	private String fhirId;

	@Column(name = "message_source")
	private String messageSource;

	@Column(name = "agent_name")
	private String agentName;

	@Column(name = "recordet_date")
	private Date recordetDate;

	@Column(name = "content_message", columnDefinition = "TEXT")
	private String contentMessage;

	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	private Calendar lastUpdate;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFhirId() {
		return fhirId;
	}

	public void setFhirId(String fhirId) {
		this.fhirId = fhirId;
	}

	public String getMessageSource() {
		return messageSource;
	}

	public void setMessageSource(String messageSource) {
		this.messageSource = messageSource;
	}

	public String getContentMessage() {
		return contentMessage;
	}

	public void setContentMessage(String contentMessage) {
		this.contentMessage = contentMessage;
	}

	public Calendar getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(Calendar lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	public String getAgentName() {
		return agentName;
	}

	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}

	public Date getRecordetDate() {
		return recordetDate;
	}

	public void setRecordetDate(Date recordetDate) {
		this.recordetDate = recordetDate;
	}
	
	

}
