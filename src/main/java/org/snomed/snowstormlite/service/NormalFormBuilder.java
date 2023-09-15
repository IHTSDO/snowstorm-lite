package org.snomed.snowstormlite.service;

import org.snomed.snowstormlite.domain.Concept;
import org.snomed.snowstormlite.domain.Relationship;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;

public class NormalFormBuilder {

	public static String getNormalForm(Concept concept, TermProvider termProvider) throws IOException {
		boolean terse = termProvider == null;

		boolean defined = concept.isDefined();
		StringBuilder builder = new StringBuilder(defined ? "===" : "<<<");
		if (!terse) builder.append(" ");

		Map<Integer, Set<Relationship>> relationships = concept.getRelationships();
		Map<String, String> terms = null;
		if (!terse) {
			Set<String> codes = new HashSet<>(concept.getParentCodes());
			for (Map.Entry<Integer, Set<Relationship>> group : relationships.entrySet()) {
				for (Relationship relationship : group.getValue()) {
					codes.add(relationship.getType().toString());
					if (!relationship.isConcrete()) {
						codes.add(relationship.getTarget().toString());
					}
				}
			}
			terms = new HashMap<>(termProvider.getTerms(codes));
			terms.put(concept.getConceptId(), concept.getPT());
		}

		if (defined) {
			for (String parentCode : concept.getParentCodes()) {
				builder.append(getCode(parentCode, terms));
				builder.append(",");
			}
			builder.deleteCharAt(builder.length() - 1);
		} else {
			builder.append(getCode(concept.getConceptId(), terms));
		}

		if (!relationships.isEmpty()) {
			builder.append(":");

			for (Map.Entry<Integer, Set<Relationship>> group : relationships.entrySet()) {
				if (group.getKey() != 0) {
					builder.append("{");
				}
				for (Relationship relationship : group.getValue()) {
					builder.append(getCode(relationship.getType().toString(), terms));
					builder.append("=");
					if (relationship.isConcrete()) {
						builder.append(relationship.getConcreteValue());
					} else {
						builder.append(getCode(relationship.getTarget().toString(), terms));
					}
					builder.append(",");
				}
				builder.deleteCharAt(builder.length() - 1);
				if (group.getKey() != 0) {
					builder.append("}");
				}
				builder.append(",");
			}
			builder.deleteCharAt(builder.length() - 1);
		}

		return builder.toString();
	}

	private static String getCode(String code, Map<String, String> terms) {
		if (terms != null) {
			return format("%s|%s|", code, terms.get(code));
		} else {
			return code;
		}
	}

}
