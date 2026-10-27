package ch.bff.producer.mapstruct;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ch.bff.producer.provider.models.CodingDto;

@Mapper(componentModel = "spring", imports = { LocalDate.class, ZoneId.class, UUID.class })
public interface CodingMapper {

	@Mapping(target = "combined", expression = "java(coding.getSystem() + \"|\" + coding.getCode() + \"|\" + coding.getDisplay())")
	@Mapping(target = "system", source = "system")
	@Mapping(target = "code", source = "code")
	@Mapping(target = "display", source = "display")
	CodingDto toCodingDto(ValueSetExpansionContainsComponent coding);
}
