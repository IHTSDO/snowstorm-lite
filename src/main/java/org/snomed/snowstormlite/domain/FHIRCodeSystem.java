package org.snomed.snowstormlite.domain;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.InstantType;
import org.snomed.snowstormlite.fhir.FHIRConstants;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static java.lang.String.format;

public class FHIRCodeSystem {

	public static final String DOC_TYPE = "code-system";

	public interface FieldNames {
		String VERSION_DATE = "version_date";
		String URI_MODULE = "module";
		String LAST_UPDATED = "last_updated";
	}
	private String versionDate;
	private Date lastUpdated;
	private String uriModule;

	public org.hl7.fhir.r4.model.CodeSystem toHapi(List<String> elements) {
		CodeSystem hapi = toHapi();

		if (elements != null) {
			if (elements.contains("filter")) {
				hapi.addFilter()
						.setCode("concept")
						.addOperator(CodeSystem.FilterOperator.ISA)
						.setDescription("Includes all concept ids that have a transitive is-a relationship with the concept id provided as the value " +
								"(including the concept itself).")
						.setValue("A SNOMED CT code");
				hapi.addFilter()
						.setCode("concept")
						.addOperator(CodeSystem.FilterOperator.DESCENDENTOF)
						.setDescription("Includes all concept ids that have a transitive is-a relationship with the code provided as the value, excluding the code itself.")
						.setValue("A SNOMED CT code");
				hapi.addFilter()
						.setCode("concept")
						.addOperator(CodeSystem.FilterOperator.IN)
						.setDescription("Includes all concept ids that are active members of the reference set identified by the concept id provided as the value.")
						.setValue("A SNOMED CT code");
				hapi.addFilter()
						.setCode("constraint")
						.addOperator(CodeSystem.FilterOperator.EQUAL)
						.setDescription("Select a set of concepts based on a formal expression constraint.")
						.setValue("A SNOMED CT Expression Constraint");
				hapi.addFilter()
						.setCode("expression")
						.addOperator(CodeSystem.FilterOperator.EQUAL)
						.setDescription("Select a set of concepts based on a formal expression constraint.")
						.setValue("A SNOMED CT Expression Constraint");
			}
			if (elements.contains("property")) {
				hapi.addProperty()
						.setCode("inactive")
						.setDescription("Whether the code is active or not (defaults to false).")
						.setType(CodeSystem.PropertyType.BOOLEAN);
				hapi.addProperty()
						.setCode("sufficientlyDefined")
						.setDescription("True if the description logic definition of the concept includes sufficient conditions (i.e. the concept is not primitive).")
						.setType(CodeSystem.PropertyType.BOOLEAN);
				hapi.addProperty()
						.setCode("parent")
						.setDescription("The SNOMED CT concept id that is a direct parent of the concept.")
						.setType(CodeSystem.PropertyType.CODE);
				hapi.addProperty()
						.setCode("child")
						.setDescription("The SNOMED CT concept id that is a direct child of the concept.")
						.setType(CodeSystem.PropertyType.CODE);
				hapi.addProperty()
						.setCode("moduleId")
						.setDescription("The SNOMED CT concept id of the module that the concept belongs to.")
						.setType(CodeSystem.PropertyType.CODE);
				hapi.addProperty()
						.setCode("normalForm")
						.setDescription("Generated Necessary Normal Form expression for the provided code or expression, with terms. " +
								"The normal form expressions are not suitable for use in subsumption testing.")
						.setType(CodeSystem.PropertyType.STRING);
				hapi.addProperty()
						.setCode("normalFormTerse")
						.setDescription("Generated Necessary Normal form expression for the provided code or expression, concept ids only. " +
								"The normal form expressions are not suitable for use in subsumption testing.")
						.setType(CodeSystem.PropertyType.STRING);
			}
		}

		return hapi;
	}

	public org.hl7.fhir.r4.model.CodeSystem toHapi() {
		org.hl7.fhir.r4.model.CodeSystem hapiCodeSystem = new org.hl7.fhir.r4.model.CodeSystem();
		hapiCodeSystem.getMeta().setProperty("versionId", new IdType("1"));
		hapiCodeSystem.getMeta().setProperty("lastUpdated", new InstantType(getLastUpdated()));
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
		hapiCodeSystem.setValueSet(getVersionUri() + "?fhir_vs");
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
		return String.join("-", "sct", uriModule, versionDate);
	}

	public String getVersionUri() {
		return format("http://snomed.info/sct/%s/version/%s", uriModule, versionDate);
	}

	public String getEditionUri() {
		return format("http://snomed.info/sct/%s", uriModule);
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

	public Date getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(Date lastUpdated) {
		this.lastUpdated = lastUpdated;
	}
}
