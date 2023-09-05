package org.snomed.snowstormmicro.service.ecl.model;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.snomed.snowstormmicro.domain.Concept;
import org.snomed.snowstormmicro.service.ecl.ExpressionConstraintLanguageService;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface SConstraint {

	BooleanQuery.Builder getQuery(BooleanQuery.Builder builder, ExpressionConstraintLanguageService eclService) throws IOException;

	static void forceNoMatch(BooleanQuery.Builder builder) {
		builder.add(termQuery(Concept.FieldNames.ID, "not-exist"), BooleanClause.Occur.MUST);
	}

	static TermQuery termQuery(String fieldName, String value) {
		return new TermQuery(new Term(fieldName, value));
	}

	static TermInSetQuery termsQuery(String fieldName, Set<String> values) {
		List<BytesRef> collect = values.stream().map(BytesRef::new).collect(Collectors.toList());
		return new TermInSetQuery(fieldName, collect);
	}

}
