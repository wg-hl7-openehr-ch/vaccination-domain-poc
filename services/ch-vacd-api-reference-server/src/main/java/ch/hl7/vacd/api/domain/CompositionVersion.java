package ch.hl7.vacd.api.domain;

import java.util.Date;

/**
 * The latest version of a Composition in EHRbase.
 *
 * @param versionUid    the version uid, e.g.
 *                      {@code <versioned_object_uid>::local.ehrbase.org::2}
 * @param timeCommitted the commit time of this version
 */
public record CompositionVersion(String versionUid, Date timeCommitted) {

	/**
	 * @return the version number, the last part of the version uid
	 */
	public String versionNumber() {
		return versionNumber(versionUid);
	}

	/**
	 * @param versionUid a version uid
	 * @return the version number, the last part of the version uid
	 */
	public static String versionNumber(String versionUid) {
		return versionUid.substring(versionUid.lastIndexOf("::") + 2);
	}

	/**
	 * @param compositionUid a version uid or a versioned object uid
	 * @return the versioned object uid, which is the same for all versions of a
	 *         Composition
	 */
	public static String versionedObjectUid(String compositionUid) {
		int idx = compositionUid.indexOf("::");
		return idx < 0 ? compositionUid : compositionUid.substring(0, idx);
	}
}
