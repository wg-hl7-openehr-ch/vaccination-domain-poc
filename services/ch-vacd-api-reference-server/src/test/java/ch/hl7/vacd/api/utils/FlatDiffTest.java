package ch.hl7.vacd.api.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class FlatDiffTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static JsonNode json(String json) throws Exception {
		return MAPPER.readTree(json);
	}

	@Test
	void between_sameContent_isEmpty() {
		FlatDiff.Diff diff = FlatDiff.between("{\"a/lot\":\"L1\",\"a/dose\":0.5}", "{\"a/dose\":0.5,\"a/lot\":\"L1\"}");

		assertTrue(diff.isEmpty());
	}

	@Test
	void between_findsChangedNewAndRemovedKeys() throws Exception {
		FlatDiff.Diff diff = FlatDiff.between("{\"a/lot\":\"L1\",\"a/dose\":0.5,\"a/route|code\":\"SC\"}",
				"{\"a/lot\":\"L2\",\"a/dose\":0.5,\"a/site\":\"arm\"}");

		assertEquals(Set.of("a/lot", "a/site"), diff.changed().keySet());
		assertEquals(json("\"L2\""), diff.changed().get("a/lot"));
		assertEquals(json("\"arm\""), diff.changed().get("a/site"));
		assertEquals(Set.of("a/route|code"), diff.removed());
		assertFalse(diff.isEmpty());
	}

	@Test
	void between_ignoresContextAndFeederAudit() {
		FlatDiff.Diff diff = FlatDiff.between(
				"{\"ctx/time\":\"t1\",\"a/_feeder_audit/original_content\":\"o1\",\"a/x:0/_feeder_audit/original_content\":\"o1\",\"ctx/language\":\"en\"}",
				"{\"ctx/time\":\"t2\",\"a/_feeder_audit/original_content\":\"o2\",\"a/x:0/_feeder_audit/original_content|formalism\":\"f\"}");

		assertTrue(diff.isEmpty());
	}

	@Test
	void apply_changesOnlyTheKeysOfTheDiff() throws Exception {
		String stored = "{\"a/_uid\":\"u::s::1\",\"a/lot\":\"L1\",\"a/route|code\":\"SC\",\"a/only_stored\":\"kept\","
				+ "\"a/_feeder_audit/original_content\":\"o1\"}";
		FlatDiff.Diff diff = FlatDiff.between("{\"a/lot\":\"L1\",\"a/route|code\":\"SC\",\"ctx/time\":\"t1\"}",
				"{\"a/lot\":\"L2\",\"a/site\":\"arm\",\"ctx/time\":\"t2\"}");

		String result = FlatDiff.apply(stored, diff);

		assertEquals(json("{\"a/_uid\":\"u::s::1\",\"a/lot\":\"L2\",\"a/only_stored\":\"kept\","
				+ "\"a/_feeder_audit/original_content\":\"o1\",\"a/site\":\"arm\"}"), json(result));
	}

	@Test
	void apply_emptyDiff_keepsTheDocument() throws Exception {
		String stored = "{\"a/lot\":\"L1\",\"a/dose\":0.5}";

		assertEquals(json(stored), json(FlatDiff.apply(stored, FlatDiff.between("{}", "{}"))));
	}
}
