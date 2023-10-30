package org.snomed.snowstormlite.fhir;

import org.hl7.fhir.r4.model.StringType;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;

import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI;
import static org.snomed.snowstormlite.fhir.FHIRConstants.SNOMED_URI_UNVERSIONED;

public class CodeSystemVersionParams {

	private final String codeSystem;
	private String snomedModule;
	private String version;
	private String id;

	public CodeSystemVersionParams(String codeSystem) {
		this.codeSystem = codeSystem;
	}

	public boolean matchesCodeSystem(FHIRCodeSystem codeSystemCandidate) {
		return codeSystemCandidate != null &&
				isSnomed() &&
				( snomedModule == null || snomedModule.equals(codeSystemCandidate.getUriModule()) ) &&
				( version == null || version.equals(codeSystemCandidate.getVersionDate()) );
	}

	public boolean isSnomed() {
		return FHIRHelper.isSnomedUri(codeSystem) || (id != null && id.startsWith("sct_"));
	}

	public boolean isUnversionedSnomed() {
		return codeSystem != null && codeSystem.startsWith(SNOMED_URI_UNVERSIONED);
	}

	public StringType toSnomedUri() {
		if (codeSystem == null || !isSnomed()) {
			return null;
		}
		return new StringType(codeSystem + (snomedModule != null ? ( "/" + snomedModule ) : "") + (version != null ? ( "/version/" + version ) : ""));
	}

	public boolean isUnspecifiedReleasedSnomed() {
		return id == null && codeSystem != null && codeSystem.equals(SNOMED_URI) && snomedModule == null;
	}

	public CodeSystemVersionParams setVersion(String version) {
		this.version = version;
		return this;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public String getVersion() {
		return version;
	}

	public void setSnomedModule(String snomedModule) {
		this.snomedModule = snomedModule;
	}

	public String getSnomedModule() {
		return snomedModule;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return "CodeSystemVersionParams{" +
				"id='" + id + '\'' +
				", system='" + codeSystem + '\'' +
				", version='" + version + '\'' +
				'}';
	}
}
