package ch.bff.producer.services;

import java.util.List;

import ch.bff.producer.provider.models.CodingDto;

public interface TerminologyReadService {

	List<CodingDto> getExpandedValueSet(String url);

}