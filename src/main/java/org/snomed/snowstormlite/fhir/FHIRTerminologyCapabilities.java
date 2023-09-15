package org.snomed.snowstormlite.fhir;

import ca.uhn.fhir.model.api.annotation.ChildOrder;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.ContactDetail;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.TerminologyCapabilities;

import java.util.Collections;

@ResourceDef(name="TerminologyCapabilities", profile="http://hl7.org/fhir/StructureDefinition/TerminologyCapabilities")
@ChildOrder(names={"url", "version", "name", "title", "status", "experimental", "date", "publisher", "contact", "description", "useContext",
		"jurisdiction", "purpose", "copyright", "kind", "software", "implementation", "lockedDate", "codeSystem", "expansion", "codeSearch",
		"validateCode", "translation", "closure"})
public class FHIRTerminologyCapabilities extends TerminologyCapabilities implements IBaseConformance {

	private static final long serialVersionUID = 1L;

	public FHIRTerminologyCapabilities withDefaults() {
		setContact();
		setCodeSystem();
		setName("SnowstormTerminologyCapabilities");
		setStatus(PublicationStatus.DRAFT);
		setTitle("Snowstorm Terminology Capability Statement");
		setVersion(getClass().getPackage().getImplementationVersion());
		return this;
	}

	private void setCodeSystem() {
		TerminologyCapabilitiesCodeSystemComponent tccsc = new TerminologyCapabilitiesCodeSystemComponent();
		tccsc.setUri("http://snomed.info/sct");
//		tccsc.setSubsumption(true);
		setCodeSystem(Collections.singletonList(tccsc));
	}

	private void setContact() {
		ContactPoint contactPoint = new ContactPoint();
		contactPoint.setSystem(ContactPointSystem.EMAIL);
		contactPoint.setValue("support@snomed.org");
		ContactDetail contactDetail = new ContactDetail();
		contactDetail.addTelecom(contactPoint);
		setContact(Collections.singletonList(contactDetail));
	}

}
