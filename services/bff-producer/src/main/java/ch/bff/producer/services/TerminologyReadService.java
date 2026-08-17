package ch.bff.producer.services;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Service;

import ch.bff.producer.client.TxClient;
import ch.bff.producer.mapstruct.CodingMapper;
import ch.bff.producer.provider.models.CodingDto;

@Service
public class TerminologyReadService {

	private final TxClient txClient;

	private final CodingMapper codingMapper;

	public TerminologyReadService(TxClient txClient, CodingMapper codingMapper) {
		this.codingMapper = codingMapper;
		this.txClient = txClient;
	}

	public List<CodingDto> getExpandedValueSet(String url) {
		Parameters parameters = new Parameters();
		parameters.addParameter().setName("url").setValue(new org.hl7.fhir.r4.model.UriType(url));
		parameters.addParameter().setName("includeDesignations").setValue(new org.hl7.fhir.r4.model.BooleanType(true));

		ValueSet valueSet = txClient.getExpandedValueSet(parameters);
		
		List<CodingDto> codingDtos = new ArrayList<>();
		valueSet.getExpansion().getContains().forEach(c -> codingDtos.add(codingMapper.toCodingDto(c)));
		
		return codingDtos;
	}

}
