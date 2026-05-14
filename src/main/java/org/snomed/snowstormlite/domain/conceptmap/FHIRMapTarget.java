package org.snomed.snowstormlite.domain.conceptmap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations;

import java.util.Objects;

import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

public class FHIRMapTarget {

	private String code;

	private String display;

	private String equivalence;

	private String comment;

	public FHIRMapTarget() {
	}

	public FHIRMapTarget(ConceptMap.TargetElementComponent hapiTarget) {
		code = hapiTarget.getCode();
		display = hapiTarget.getDisplay();
		equivalence = hapiTarget.getEquivalence() != null ? hapiTarget.getEquivalence().toCode() : null;
		comment = hapiTarget.getComment();
	}

	@JsonIgnore
	public ConceptMap.TargetElementComponent getHapi() {
		ConceptMap.TargetElementComponent component = new ConceptMap.TargetElementComponent();
		component.setCode(code);
		component.setDisplay(display);
		if (equivalence != null) {
			component.setEquivalence(Enumerations.ConceptMapEquivalence.fromCode(equivalence));
		}
		component.setComment(comment);
		return component;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDisplay() {
		return display;
	}

	public void setDisplay(String display) {
		this.display = display;
	}

	public String getEquivalence() {
		return equivalence;
	}

	public void setEquivalence(String equivalence) {
		this.equivalence = equivalence;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FHIRMapTarget that = (FHIRMapTarget) o;
		return Objects.equals(code, that.code) && Objects.equals(equivalence, that.equivalence);
	}

	@Override
	public int hashCode() {
		return Objects.hash(code, equivalence);
	}
}
