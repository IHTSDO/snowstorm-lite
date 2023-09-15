package org.snomed.snowstormmicro.service;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.snomed.snowstormmicro.domain.Concept;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class QueryHelper {

	public static void forceNoMatch(BooleanQuery.Builder builder) {
		builder.add(termQuery(Concept.FieldNames.ID, "not-exist"), BooleanClause.Occur.MUST);
	}

	public static TermQuery termQuery(String fieldName, String value) {
		return new TermQuery(new Term(fieldName, value));
	}

	public static TermInSetQuery termsQuery(String fieldName, Collection<String> values) {
		List<BytesRef> collect = values.stream().map(BytesRef::new).collect(Collectors.toList());
		return new TermInSetQuery(fieldName, collect);
	}
}
