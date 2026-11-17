package ch.hl7.vacd.api.repo;

import ca.uhn.fhir.context.FhirContext;
import ch.hl7.vacd.api.entity.ResourceEntity;
import ch.hl7.vacd.api.entity.ResourceIdentifierEntity;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ResourceRepositoryIntegrationTest {

    @Autowired
    private ResourceRepository repo;

    @Autowired
    private FhirContext fhirContext;

    @Test
    public void testCrudLifecycle() {
        // Create
        Patient p = new Patient();
        p.addName().setFamily("TestFam").addGiven("John");
        String idPart = UUID.randomUUID().toString();
        p.setId("Patient/" + idPart);
        String json = fhirContext.newJsonParser().encodeResourceToString(p);

        ResourceEntity entity = new ResourceEntity();
        entity.setResourceType("Patient");
        entity.setResourceId(idPart);
        entity.setJson(json);

        ResourceEntity saved = repo.save(entity);
        assertNotNull(saved.getId());

        // Read
        List<ResourceEntity> found = repo.findByResourceTypeAndResourceId("Patient", idPart);
        assertFalse(found.isEmpty());
        Patient parsed = (Patient) fhirContext.newJsonParser().parseResource(found.get(0).getJson());
        assertEquals("TestFam", parsed.getNameFirstRep().getFamily());

        // Search (by type)
        List<ResourceEntity> byType = repo.findByResourceType("Patient");
        assertTrue(byType.size() >= 1);

        // Update
        parsed.getNameFirstRep().setFamily("UpdatedFam");
        String json2 = fhirContext.newJsonParser().encodeResourceToString(parsed);
        ResourceEntity toUpdate = found.get(0);
        toUpdate.setJson(json2);
        repo.save(toUpdate);

        List<ResourceEntity> found2 = repo.findByResourceTypeAndResourceId("Patient", idPart);
        Patient parsed2 = (Patient) fhirContext.newJsonParser().parseResource(found2.get(0).getJson());
        assertEquals("UpdatedFam", parsed2.getNameFirstRep().getFamily());

        // Delete
        repo.delete(toUpdate);
        List<ResourceEntity> afterDelete = repo.findByResourceTypeAndResourceId("Patient", idPart);
        assertTrue(afterDelete.isEmpty());
    }

    @Test
    public void testFindByResourceTypeAndIdentifier() {
        String idPart = UUID.randomUUID().toString();
        String identifier = "urn:uuid:" + UUID.randomUUID();

        ResourceEntity entity = new ResourceEntity();
        entity.setResourceType("Immunization");
        entity.setResourceId(idPart);
        entity.setJson("{}");
        entity.addIdentifier(new ResourceIdentifierEntity().setIdSystem("urn:ietf:rfc:3986").setIdValue(identifier)
                .setResourceEntity(entity));
        entity.addIdentifier(new ResourceIdentifierEntity().setIdSystem("urn:che:epr:ch-vacd:composition-uid")
                .setIdValue("some-uid::local.ehrbase.org::1").setResourceEntity(entity));
        repo.save(entity);

        List<ResourceEntity> found = repo.findByResourceTypeAndIdentifier("Immunization", "urn:ietf:rfc:3986",
                identifier);

        assertEquals(1, found.size());
        assertEquals(idPart, found.get(0).getResourceId());
        // all identifiers are fetched, also outside of a transaction
        assertEquals(2, found.get(0).getIdentifiers().size());

        assertTrue(repo.findByResourceTypeAndIdentifier("Patient", "urn:ietf:rfc:3986", identifier).isEmpty());
        assertTrue(repo.findByResourceTypeAndIdentifier("Immunization", "urn:che:epr:ch-vacd:composition-uid",
                identifier).isEmpty());

        repo.delete(found.get(0));
    }
}
