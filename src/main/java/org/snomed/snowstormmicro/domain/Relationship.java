package org.snomed.snowstormmicro.domain;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Objects;

public class Relationship implements Comparable<Relationship> {

	private static final Comparator<Relationship> RELATIONSHIP_COMPARATOR = Comparator
			.comparing(Relationship::getType)
			.thenComparing(Relationship::getTarget, Comparator.nullsLast(Long::compareTo))
			.thenComparing(Relationship::getConcreteValue, Comparator.nullsLast(String::compareTo));

	private final int group;
	private final Long type;
	private final Long target;
	private final String concreteValue;

	public Relationship(int group, Long type, Long target, String concreteValue) {
		this.group = group;
		this.type = type;
		this.target = target;
		this.concreteValue = concreteValue;
	}

	public boolean isConcrete() {
		return concreteValue != null;
	}

	public int getGroup() {
		return group;
	}

	public Long getType() {
		return type;
	}

	public Long getTarget() {
		return target;
	}

	public String getConcreteValue() {
		return concreteValue;
	}

	@Override
	public int compareTo(@NotNull Relationship other) {
		return RELATIONSHIP_COMPARATOR.compare(this, other);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Relationship that = (Relationship) o;
		return Objects.equals(type, that.type) && Objects.equals(target, that.target) && Objects.equals(concreteValue, that.concreteValue);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, target, concreteValue);
	}
}
