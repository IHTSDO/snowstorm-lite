package org.snomed.snowstormlite.domain.conceptmap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hl7.fhir.r4.model.ConceptMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.snomed.snowstormlite.util.CollectionUtils.orEmpty;

public class FHIRMapElement {

	private String id;

	private String groupId;

	private String code;

	private String display;

	private List<FHIRMapTarget> target;

	private String message;

	public FHIRMapElement() {
	}

	public FHIRMapElement(ConceptMap.SourceElementComponent hapiElement, String groupId) {
		this.groupId = groupId;
		code = hapiElement.getCode();
		display = hapiElement.getDisplay();
		target = new ArrayList<>();
		for (ConceptMap.TargetElementComponent hapiTarget : hapiElement.getTarget()) {
			target.add(new FHIRMapTarget(hapiTarget));
		}
	}

	@JsonIgnore
	public ConceptMap.SourceElementComponent getHapi() {
		ConceptMap.SourceElementComponent element = new ConceptMap.SourceElementComponent();
		element.setCode(code);
		element.setDisplay(display);
		for (FHIRMapTarget mapTarget : orEmpty(target)) {
			element.addTarget(mapTarget.getHapi());
		}
		return element;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getCode() {
		return code;
	}

	public FHIRMapElement setCode(String code) {
		this.code = code;
		return this;
	}

	public String getDisplay() {
		return display;
	}

	public void setDisplay(String display) {
		this.display = display;
	}

	public List<FHIRMapTarget> getTarget() {
		return target;
	}

	public FHIRMapElement setTarget(List<FHIRMapTarget> target) {
		this.target = target;
		return this;
	}

	@JsonIgnore
	public String getMessage() {
		return message;
	}

	public FHIRMapElement setMessage(String message) {
		this.message = message;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FHIRMapElement that = (FHIRMapElement) o;
		return Objects.equals(groupId, that.groupId) && Objects.equals(code, that.code) && Objects.equals(target, that.target);
	}

	@Override
	public int hashCode() {
		return Objects.hash(groupId, code, target);
	}
}
