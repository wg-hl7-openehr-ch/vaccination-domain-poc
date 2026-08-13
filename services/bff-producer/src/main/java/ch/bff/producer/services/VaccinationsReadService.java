package ch.bff.producer;

import ch.bff.producer.client.FhirClient;
import ch.bff.producer.mapstruct.VaccinationsMapper;
import ch.bff.producer.provider.models.VaccinationDto;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class VacctinationsReadService {

    public static final String IMMUNIZATION_LOINC_CODE = "11369-6";
    private final FhirClient fhirClient;
    private final VaccinationsMapper vaccinationsMapper;

    public VacctinationsReadService(FhirClient fhirClient, VaccinationsMapper vaccinationsMapper) {
        this.fhirClient = fhirClient;
        this.vaccinationsMapper = vaccinationsMapper;
    }

    public List<VaccinationDto> getVaccinationList(String patientIamId) {
        var params = new Parameters();
        // params.addParameter().setName("patientId").setValue(new StringType(patientIamId));
        params.addParameter().setName("type").setValue(new Coding().setSystem("urn:oid:2.16.756.5.30.1.127.3.10.10").setCode("urn:che:epr:ch-vacd:vaccination-record:2022"));
        var bundle = fhirClient.getVaccinationRecord(patientIamId, params);

        Map<String, Immunization> immunizationMap = new HashMap<>();
        for (var entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Immunization imm) {
                if (entry.getFullUrl() != null) {
                    immunizationMap.put(entry.getFullUrl(), imm);
                }
                String idPart = imm.getIdElement().getIdPart();
                immunizationMap.put(imm.fhirType() + "/" + idPart, imm);
            }
        }

        var practitionerRoleDisplayMap = buildPractitionerRoleDisplayMap(bundle);

        return bundle.getEntry().stream()
                .filter(e -> e.getResource() instanceof org.hl7.fhir.r4.model.Composition)
                .map(e -> (org.hl7.fhir.r4.model.Composition) e.getResource())
                .flatMap(composition -> composition.getSection().stream())
                .filter(section -> section.getCode().getCoding().stream()
                        .anyMatch(coding -> "http://loinc.org".equals(coding.getSystem()) && IMMUNIZATION_LOINC_CODE.equals(coding.getCode())))
                .flatMap(section -> section.getEntry().stream())
                .map(ref -> resolveImmunization(ref, immunizationMap))
                .filter(Objects::nonNull)
                .map(imm -> {
                    resolvePractitionerDisplay(imm, practitionerRoleDisplayMap);
                    return imm;
                })
                .map(vaccinationsMapper::toVaccinationDto)
                .toList();
    }

    private Immunization resolveImmunization(Reference ref, Map<String, Immunization> immunizationMap) {
        if (ref.getResource() instanceof Immunization imm) {
            return imm;
        }
        String refStr = ref.getReference();
        if (refStr != null) {
            return immunizationMap.get(refStr);
        }
        return null;
    }

    static Map<String, String> buildPractitionerRoleDisplayMap(Bundle bundle) {
        Map<String, String> pracRefToDisplay = new HashMap<>();
        for (var entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Practitioner p && p.hasName()) {
                var ref = p.fhirType() + "/" + p.getIdElement().getIdPart();
                pracRefToDisplay.put(ref, p.getNameFirstRep().getNameAsSingleString());
                if (entry.getFullUrl() != null) {
                    pracRefToDisplay.put(entry.getFullUrl(), p.getNameFirstRep().getNameAsSingleString());
                }
            }
        }

        Map<String, String> roleDisplayMap = new HashMap<>();
        for (var entry : bundle.getEntry()) {
            if (entry.getResource() instanceof PractitionerRole pr) {
                var roleRef = pr.fhirType() + "/" + pr.getIdElement().getIdPart();
                if (entry.getFullUrl() != null) {
                    roleDisplayMap.put(entry.getFullUrl(), resolveNameFromPracRef(pr, pracRefToDisplay, entry.getFullUrl()));
                }
                roleDisplayMap.put(roleRef, resolveNameFromPracRef(pr, pracRefToDisplay, roleRef));
            }
        }
        return roleDisplayMap;
    }

    private static String resolveNameFromPracRef(PractitionerRole pr, Map<String, String> pracRefToDisplay, String fallbackKey) {
        var pracRef = pr.getPractitioner().getReference();
        if (pracRef != null) {
            var name = pracRefToDisplay.get(pracRef);
            if (name != null) return name;
        }
        return fallbackKey;
    }

    private void resolvePractitionerDisplay(Immunization imm, Map<String, String> practitionerRoleDisplayMap) {
        for (var performer : imm.getPerformer()) {
            var actor = performer.getActor();
            if (actor == null || actor.hasDisplay()) continue;
            var ref = actor.getReference();
            if (ref != null) {
                var displayName = practitionerRoleDisplayMap.get(ref);
                if (displayName != null) {
                    actor.setDisplay(displayName);
                }
            }
        }
    }
}
