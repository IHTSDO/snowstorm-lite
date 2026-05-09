package et.medrafa.terminology.domain;

import org.hl7.fhir.r4.model.Enumerations;
import et.medrafa.terminology.fhir.FHIRHelper;

import static java.lang.String.format;

public record FHIRSnomedImplicitMap(String refsetId, String sourceSystem, String targetSystem, Enumerations.ConceptMapEquivalence equivalence) {

	public boolean isFromSnomed() {
		return FHIRHelper.isSnomedUri(sourceSystem);
	}

	public boolean isToSnomed() {
		return FHIRHelper.isSnomedUri(targetSystem);
	}

	public String getUrl(FHIRCodeSystem codeSystem) {
		return format("%s?fhir_cm=%s", codeSystem.getVersionUri(), refsetId);
	}

}
