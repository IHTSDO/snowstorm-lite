package org.snomed.snowstormlite.service;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.snomed.snowstormlite.domain.Concept;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class QueryHelper {

	public static final String TYPE = "_type";

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

	public static TermInSetQuery termsQueryFromLongs(String fieldName, Collection<Long> values) {
		List<BytesRef> collect = values.stream().map(value -> new BytesRef(value.toString())).collect(Collectors.toList());
		return new TermInSetQuery(fieldName, collect);
	}
}
