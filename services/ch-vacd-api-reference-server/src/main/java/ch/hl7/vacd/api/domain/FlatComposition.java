package ch.hl7.vacd.api.domain;

/**
 * A Composition in FLAT format together with the version it was read from.
 *
 * @param versionUid the version uid, e.g.
 *                   {@code <versioned_object_uid>::local.ehrbase.org::2}
 * @param flat       the Composition as FLAT JSON
 */
public record FlatComposition(String versionUid, String flat) {
}
