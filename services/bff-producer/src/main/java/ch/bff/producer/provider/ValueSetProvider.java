package ch.bff.producer.provider;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.bff.producer.provider.models.CodingDto;
import ch.bff.producer.services.TerminologyReadService;

@RestController
@RequestMapping("/api/valuesets")
public class ValueSetProvider {

	private final TerminologyReadService terminologyReadService;

	public ValueSetProvider(TerminologyReadService terminologyReadService) {
		this.terminologyReadService = terminologyReadService;
	}

	@GetMapping("vaccines")
	public List<CodingDto> getVaccines() {

		return terminologyReadService.getExpandedValueSet("http://fhir.ch/ig/ch-vacd/ValueSet/ch-vacd-vaccines-vs");
	}

}
