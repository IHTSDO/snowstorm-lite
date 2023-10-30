package org.snomed.snowstormlite.domain;

import org.hl7.fhir.r4.model.Enumerations;
import org.snomed.snowstormlite.fhir.FHIRConstants;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static java.lang.String.format;

public class FHIRCodeSystem {

	public static final String DOC_TYPE = "code-system";

	public interface FieldNames {
		String VERSION_DATE = "version_date";
		String URI_MODULE = "module";
	}
	private String versionDate;
	private String uriModule;

	public org.hl7.fhir.r4.model.CodeSystem toHapi() {
		org.hl7.fhir.r4.model.CodeSystem hapiCodeSystem = new org.hl7.fhir.r4.model.CodeSystem();
		hapiCodeSystem.setUrl(FHIRConstants.SNOMED_URI);
		GregorianCalendar gregorianCalendar = new GregorianCalendar();
		gregorianCalendar.clear();
		int year = Integer.parseInt(versionDate.substring(0, 4));
		gregorianCalendar.set(Calendar.YEAR, year);
		int month = Integer.parseInt(versionDate.substring(4, 6));
		gregorianCalendar.set(Calendar.MONTH, month - 1);
		int day = Integer.parseInt(versionDate.substring(6, 8));
		gregorianCalendar.set(Calendar.DAY_OF_MONTH, day);
		hapiCodeSystem.setDate(gregorianCalendar.getTime());
		hapiCodeSystem.setVersion(getVersionUri());
		hapiCodeSystem.setId(getId());
		hapiCodeSystem.setName(format("SNOMED CT, Edition %s, version %s", uriModule, versionDate));
		hapiCodeSystem.setTitle(getTitle());
		hapiCodeSystem.setPublisher("SNOMED International");
		hapiCodeSystem.setStatus(Enumerations.PublicationStatus.ACTIVE);
		hapiCodeSystem.setHierarchyMeaning(org.hl7.fhir.r4.model.CodeSystem.CodeSystemHierarchyMeaning.ISA);
		hapiCodeSystem.setCompositional(true);
		hapiCodeSystem.setContent(org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode.COMPLETE);
		return hapiCodeSystem;
	}

	public String getTitle() {
		return "SNOMED CT";
	}

	private String getId() {
		return String.join("_", "sct", uriModule, versionDate);
	}

	public String getVersionUri() {
		return format("http://snomed.info/sct/%s/version/%s", uriModule, versionDate);
	}

	public String getSystemAndVersionUri() {
		return format("http://snomed.info/sct|http://snomed.info/sct/%s/version/%s", uriModule, versionDate);
	}

	public String getUriModule() {
		return uriModule;
	}

	public void setUriModule(String uriModule) {
		this.uriModule = uriModule;
	}

	public String getVersionDate() {
		return versionDate;
	}

	public void setVersionDate(String versionDate) {
		this.versionDate = versionDate;
	}

}
