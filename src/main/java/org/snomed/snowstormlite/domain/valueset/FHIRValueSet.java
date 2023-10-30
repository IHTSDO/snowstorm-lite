package org.snomed.snowstormlite.domain.valueset;

import org.hl7.fhir.r4.model.ContactDetail;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ValueSet;
import org.snomed.snowstormlite.domain.FHIRIdentifier;
import org.snomed.snowstormlite.domain.contact.FHIRContactDetail;

import java.util.ArrayList;
import java.util.List;

import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

public class FHIRValueSet {

	public static final String DOC_TYPE = "vs";

	public interface Fields {

		String ID = "id";
		String URL = "url";
		String VERSION = "version";
		String NAME = "name";
		String STATUS = "status";
		String EXPERIMENTAL = "exp";
		String DESCRIPTION = "desc";
		String SERIALISED = "serialised";
	}

	private String id;

	private String url;

	private List<FHIRIdentifier> identifier;

	private String version;

	private String name;

	private String title;

	private String status;

	private Boolean experimental;

	private String publisher;

	private List<FHIRContactDetail> contact;

	private String description;

	private String purpose;

	private String copyright;

	private FHIRValueSetCompose compose;

	public FHIRValueSet() {
	}

	public FHIRValueSet(ValueSet hapiValueSet) {
		this();
		id = hapiValueSet.getIdElement().getIdPart();
		url = hapiValueSet.getUrl();
		for (Identifier hapiIdentifier : hapiValueSet.getIdentifier()) {
			if (identifier == null) {
				identifier = new ArrayList<>();
			}
			identifier.add(new FHIRIdentifier(hapiIdentifier));
		}
		version = hapiValueSet.getVersion();
		name = hapiValueSet.getName();
		title = hapiValueSet.getTitle();
		status = hapiValueSet.getStatus() != null ? hapiValueSet.getStatus().toCode() : null;
		experimental = hapiValueSet.hasExperimental() ? hapiValueSet.getExperimental() : null;
		publisher = hapiValueSet.getPublisher();
		for (ContactDetail contactDetail : hapiValueSet.getContact()) {
			if (contact == null) {
				contact = new ArrayList<>();
			}
			contact.add(new FHIRContactDetail(contactDetail));
		}
		description = hapiValueSet.getDescription();
		purpose = hapiValueSet.getPurpose();
		copyright = hapiValueSet.getCopyright();

		compose = new FHIRValueSetCompose(hapiValueSet.getCompose());
	}

	public ValueSet toHapi() {
		ValueSet valueSet = new ValueSet();
		valueSet.setId(id);
		valueSet.setUrl(url);

		for (FHIRIdentifier fhirIdentifier : orEmpty(getIdentifier())) {
			valueSet.addIdentifier(fhirIdentifier.getHapi());
		}
		valueSet.setVersion(version);
		valueSet.setName(name);
		valueSet.setTitle(title);
		valueSet.setStatus(status != null ? Enumerations.PublicationStatus.fromCode(status.toLowerCase()) : Enumerations.PublicationStatus.UNKNOWN);
		if (experimental != null) {
			valueSet.setExperimental(experimental);
		}
		valueSet.setPublisher(publisher);
		for (FHIRContactDetail contactDetail : orEmpty(contact)) {
			valueSet.addContact(contactDetail.getHapi());
		}
		valueSet.setDescription(description);
		valueSet.setPurpose(purpose);
		valueSet.setCopyright(copyright);

		valueSet.setCompose(compose.toHapi());
		return valueSet;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public List<FHIRIdentifier> getIdentifier() {
		return identifier;
	}

	public void setIdentifier(List<FHIRIdentifier> identifier) {
		this.identifier = identifier;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Boolean getExperimental() {
		return experimental;
	}

	public void setExperimental(Boolean experimental) {
		this.experimental = experimental;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public List<FHIRContactDetail> getContact() {
		return contact;
	}

	public void setContact(List<FHIRContactDetail> contact) {
		this.contact = contact;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public String getCopyright() {
		return copyright;
	}

	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}

	public FHIRValueSetCompose getCompose() {
		return compose;
	}

	public void setCompose(FHIRValueSetCompose compose) {
		this.compose = compose;
	}
}
