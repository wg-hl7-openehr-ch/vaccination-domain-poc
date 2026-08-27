package ch.bff.producer.client;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "fhir-server", url = "${FHIR_BASE_URL:http://localhost:9111/ch-vacd-api-reference-server/fhir}")
public interface FhirClient {

    @GetMapping("/Patient")
    Bundle getPatient();

    @GetMapping("/Patient/{id}")
    org.hl7.fhir.r4.model.Patient getPatientById(@PathVariable("id") String id);

    @PostMapping("/Patient")
    org.hl7.fhir.r4.model.Patient createPatient(@RequestBody org.hl7.fhir.r4.model.Patient patient);

    /**
     * Beispiel: https://fhir.ch/ig/ch-vacd/4.0.0/Bundle-1-3-VaccinationRecord.json.html
     *
     * @param parameters
     * {
     *   "resourceType": "Parameters",
     *   "parameter": [
     *     {
     *       "name": "type",
     *       "valueCoding": {
     *         "system": "urn:oid:2.16.756.5.30.1.127.3.10.10",
     *         "code": "urn:che:epr:ch-vacd:vaccination-record:2022"
     *       }
     *     }
     *   ]
     * }
     * @return Bundle with all vaccinations (VaccinationRecord)
     */
    @PostMapping("/Patient/{id}/$export-document")
    Bundle getVaccinationRecord(@PathVariable("id") String id, @RequestBody Parameters parameters);

    /**
     * https://fhir.ch/ig/ch-vacd/6.0.0/immunization-administration-document.html
     * Required Sections:
     * - Section: Immunization Administration
     * Required Entries:
     * - Entry: Patient
     * - Entry: Immunization
     * - Entry: Organization
     * - Entry: Practitioner
     * - Entry: PractitionerRole
     *
     * @param immunizationAdministrationBundle
     * @return Bundle with all vaccinations (VaccinationRecord)
     */
    @PostMapping("/Bundle")
    Bundle postImmunizationAdministrationBundle(@RequestBody Bundle immunizationAdministrationBundle);
}
