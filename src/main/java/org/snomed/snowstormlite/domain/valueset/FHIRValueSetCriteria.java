package org.snomed.snowstormlite.domain.valueset;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.ValueSet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

public class FHIRValueSetCriteria {

	private String system;

	private String version;

	private List<String> codes;

	private List<FHIRValueSetFilter> filter;

	private List<String> valueSet;

	public FHIRValueSetCriteria() {
	}

	public FHIRValueSetCriteria(ValueSet.ConceptSetComponent hapiCriteria) {
		system = hapiCriteria.getSystem();
		version = hapiCriteria.getVersion();
		for (ValueSet.ConceptReferenceComponent code : hapiCriteria.getConcept()) {
			if (codes == null) {
				codes = new ArrayList<>();
			}
			codes.add(code.getCode());
		}
		for (ValueSet.ConceptSetFilterComponent hapiFilter : hapiCriteria.getFilter()) {
			if (filter == null) {
				filter = new ArrayList<>();
			}
			filter.add(new FHIRValueSetFilter(hapiFilter));
		}
		valueSet = hapiCriteria.getValueSet().stream().map(CanonicalType::getValueAsString).collect(Collectors.toList());
	}

	public ValueSet.ConceptSetComponent toHapi() {
		ValueSet.ConceptSetComponent hapiConceptSet = new ValueSet.ConceptSetComponent();
		hapiConceptSet.setSystem(system);
		hapiConceptSet.setVersion(version);
		for (String code : orEmpty(codes)) {
			ValueSet.ConceptReferenceComponent component = new ValueSet.ConceptReferenceComponent();
			component.setCode(code);
			hapiConceptSet.addConcept(component);
		}
		for (FHIRValueSetFilter filter : orEmpty(getFilter())) {
			hapiConceptSet.addFilter(filter.toHapi());
		}
		hapiConceptSet.setValueSet(orEmpty(valueSet).stream().map(CanonicalType::new).collect(Collectors.toList()));
		return hapiConceptSet;
	}

	public String getSystem() {
		return system;
	}

	public void setSystem(String system) {
		this.system = system;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public List<String> getCodes() {
		return codes;
	}

	public void setCodes(List<String> codes) {
		this.codes = codes;
	}

	public List<FHIRValueSetFilter> getFilter() {
		return filter;
	}

	public void setFilter(List<FHIRValueSetFilter> filter) {
		this.filter = filter;
	}
}
