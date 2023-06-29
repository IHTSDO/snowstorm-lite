package org.snomed.snowstormmicro.domain;

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
		hapiCodeSystem.setUrl("http://snomed.info/sct");
		hapiCodeSystem.setId("snomed");
		if (versionDate != null && versionDate.matches("\\d{8}")) {
			GregorianCalendar gregorianCalendar = new GregorianCalendar();
			gregorianCalendar.clear();
			gregorianCalendar.set(Calendar.YEAR, Integer.parseInt(versionDate.substring(0, 4)));
			gregorianCalendar.set(Calendar.MONTH, Integer.parseInt(versionDate.substring(4, 6)));
			gregorianCalendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(versionDate.substring(6, 8)));
			hapiCodeSystem.setDate(gregorianCalendar.getTime());
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
