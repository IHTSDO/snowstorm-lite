package org.snomed.snowstormmicro.domain;

import org.snomed.snowstormmicro.fhir.FHIRConstants;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class CodeSystem {

	public static final String DOC_TYPE = "code-system";

	public interface FieldNames {
		String VERSION_DATE = "version_date";
	}
	private String versionDate;

	public org.hl7.fhir.r4.model.CodeSystem toHapi() {
		org.hl7.fhir.r4.model.CodeSystem hapiCodeSystem = new org.hl7.fhir.r4.model.CodeSystem();
		hapiCodeSystem.setName("SNOMED CT");
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
			// TODO: Set module / Edition URI
//			String moduleId = ?
//			hapiCodeSystem.setVersion(format("%s/%s/%s", FHIRConstants.SNOMED_URI, moduleId, versionDate));
		}
		return hapiCodeSystem;
	}

	public String getVersionDate() {
		return versionDate;
	}

	public void setVersionDate(String versionDate) {
		this.versionDate = versionDate;
	}
}
