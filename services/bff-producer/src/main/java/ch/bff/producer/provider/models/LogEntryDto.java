package ch.bff.producer.provider.models;

import java.util.Date;

public record LogEntryDto(Date dateTime, String artefactType, String artefact) {

}
