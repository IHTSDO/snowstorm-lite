package org.snomed.snowstormmicro.domain;

import org.hl7.fhir.r4.model.Enumerations;
import org.snomed.snowstormmicro.fhir.FHIRConstants;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static java.lang.String.format;

public class CodeSystem {

	public static final String DOC_TYPE = "code-system";

	public interface FieldNames {
		String VERSION_DATE = "version_date";
		String VERSION_URI = "version_uri";
	}
	private String versionDate;
	private String versionUri;

	public org.hl7.fhir.r4.model.CodeSystem toHapi() {
		org.hl7.fhir.r4.model.CodeSystem hapiCodeSystem = new org.hl7.fhir.r4.model.CodeSystem();
		hapiCodeSystem.setUrl(FHIRConstants.SNOMED_URI);
		hapiCodeSystem.setId("snomed");
		if (versionDate != null && versionDate.matches("\\d{8}")) {
			GregorianCalendar gregorianCalendar = new GregorianCalendar();
			gregorianCalendar.clear();
			int year = Integer.parseInt(versionDate.substring(0, 4));
			gregorianCalendar.set(Calendar.YEAR, year);
			int month = Integer.parseInt(versionDate.substring(4, 6));
			gregorianCalendar.set(Calendar.MONTH, month - 1);
			int day = Integer.parseInt(versionDate.substring(6, 8));
			gregorianCalendar.set(Calendar.DAY_OF_MONTH, day);
			hapiCodeSystem.setDate(gregorianCalendar.getTime());
			hapiCodeSystem.setVersion(versionUri);
			String[] split = versionUri.split("/");
			if (split.length == 7) {
				String module = split[4];
				String version = split[6];
				hapiCodeSystem.setId(String.join("_", "sct", module, version));
				hapiCodeSystem.setName(format("SNOMED CT, Edition %s, version %s", module, version));
			}
			hapiCodeSystem.setTitle("SNOMED CT");
			hapiCodeSystem.setPublisher("SNOMED International");
			hapiCodeSystem.setStatus(Enumerations.PublicationStatus.ACTIVE);
			hapiCodeSystem.setHierarchyMeaning(org.hl7.fhir.r4.model.CodeSystem.CodeSystemHierarchyMeaning.ISA);
			hapiCodeSystem.setCompositional(true);
			hapiCodeSystem.setContent(org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode.COMPLETE);
		}
		return hapiCodeSystem;
	}

	public String getVersionDate() {
		return versionDate;
	}

	public void setVersionDate(String versionDate) {
		this.versionDate = versionDate;
	}

	public String getVersionUri() {
		return versionUri;
	}

	public void setVersionUri(String versionUri) {
		this.versionUri = versionUri;
	}
}
