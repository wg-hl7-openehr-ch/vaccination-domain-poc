package ch.hl7.vacd.api.utils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Difference between two openEHR FLAT documents, and its application to a
 * third one.
 *
 * Used to correct a stored Composition: the mapping FHIR to openEHR does not
 * return everything a Composition holds, so writing the mapped correction as a
 * whole would drop those fields. Instead the state before and after the
 * correction are both mapped the same way, and only the keys that differ
 * between the two are applied to the stored Composition.
 */
public final class FlatDiff {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private FlatDiff() {
	}

	/**
	 * @param changed the keys with a new or changed value, and that value
	 * @param removed the keys that are gone
	 */
	public record Diff(Map<String, JsonNode> changed, Set<String> removed) {

		public boolean isEmpty() {
			return changed.isEmpty() && removed.isEmpty();
		}
	}

	/**
	 * The keys in which {@code flatAfter} differs from {@code flatBefore}. Keys of
	 * the composition context ({@code ctx/...}) and of the feeder audit are not
	 * compared: they describe the commit, not the content.
	 */
	public static Diff between(String flatBefore, String flatAfter) {
		ObjectNode before = read(flatBefore);
		ObjectNode after = read(flatAfter);

		Map<String, JsonNode> changed = new LinkedHashMap<>();
		Set<String> removed = new LinkedHashSet<>();
		for (Map.Entry<String, JsonNode> entry : after.properties()) {
			if (!ignored(entry.getKey()) && !entry.getValue().equals(before.get(entry.getKey()))) {
				changed.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, JsonNode> entry : before.properties()) {
			if (!ignored(entry.getKey()) && !after.has(entry.getKey())) {
				removed.add(entry.getKey());
			}
		}
		return new Diff(changed, removed);
	}

	/**
	 * @return {@code flat} with the diff applied; all other keys are kept as they
	 *         are
	 */
	public static String apply(String flat, Diff diff) {
		ObjectNode result = read(flat);
		result.remove(diff.removed());
		diff.changed().forEach(result::set);
		try {
			return MAPPER.writeValueAsString(result);
		} catch (Exception e) {
			throw new RuntimeException("Failed to write FLAT JSON", e);
		}
	}

	private static boolean ignored(String key) {
		return key.startsWith("ctx/") || key.contains("/_feeder_audit");
	}

	private static ObjectNode read(String flat) {
		try {
			return (ObjectNode) MAPPER.readTree(flat);
		} catch (Exception e) {
			throw new RuntimeException("Failed to read FLAT JSON", e);
		}
	}
}
