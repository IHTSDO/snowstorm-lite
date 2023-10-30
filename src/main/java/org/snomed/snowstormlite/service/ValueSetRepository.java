package org.snomed.snowstormlite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.snomed.snowstormlite.domain.valueset.FHIRValueSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ValueSetRepository {

	@Autowired
	private IndexIOProvider indexIOProvider;

	@Autowired
	private ObjectMapper objectMapper;

	public FHIRValueSet findValueSet(String url, String version) throws IOException {
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		BooleanQuery.Builder builder = new BooleanQuery.Builder()
				.add(new TermQuery(new Term(CodeSystemRepository.TYPE, FHIRValueSet.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(FHIRValueSet.Fields.URL, url)), BooleanClause.Occur.MUST);
		if (version != null) {
			builder.add(new TermQuery(new Term(FHIRValueSet.Fields.VERSION, version)), BooleanClause.Occur.MUST);
		}
		TopDocs topDocs = indexSearcher.search(builder.build(), 1, new Sort(new SortField(FHIRValueSet.Fields.VERSION, SortField.Type.DOC)));
		if (topDocs.totalHits.value > 0) {
			return getVSFromIndex(topDocs.scoreDocs[0], indexSearcher.storedFields());
		}
		return null;
	}

	public FHIRValueSet findValueSetById(String id) throws IOException {
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		TopDocs topDocs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(CodeSystemRepository.TYPE, FHIRValueSet.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(FHIRValueSet.Fields.ID, id)), BooleanClause.Occur.MUST)
				.build(), 1);
		if (topDocs.totalHits.value > 0) {
			return getVSFromIndex(topDocs.scoreDocs[0], indexSearcher.storedFields());
		}
		return null;
	}

	public List<FHIRValueSet> findAll() throws IOException {
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		TopDocs topDocs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(CodeSystemRepository.TYPE, FHIRValueSet.DOC_TYPE)), BooleanClause.Occur.MUST)
				.build(),
				10_000, new Sort(new SortField(FHIRValueSet.Fields.URL, SortField.Type.DOC), new SortField(FHIRValueSet.Fields.VERSION, SortField.Type.DOC, true)));

		List<FHIRValueSet> all = new ArrayList<>();
		StoredFields storedFields = indexSearcher.getIndexReader().storedFields();
		for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
			all.add(getVSFromIndex(scoreDoc, storedFields));
		}
		return all;
	}

	public void save(FHIRValueSet internalValueSet) throws IOException {
		Document document = new Document();
		document.add(new StringField(CodeSystemRepository.TYPE, FHIRValueSet.DOC_TYPE, Field.Store.YES));
		document.add(new StringField(FHIRValueSet.Fields.ID, internalValueSet.getId(), Field.Store.YES));
		document.add(new StringField(FHIRValueSet.Fields.URL, internalValueSet.getUrl(), Field.Store.YES));
		document.add(new SortedDocValuesField(FHIRValueSet.Fields.URL, new BytesRef(internalValueSet.getUrl())));
		document.add(new StringField(FHIRValueSet.Fields.VERSION, internalValueSet.getVersion(), Field.Store.YES));
		document.add(new SortedDocValuesField(FHIRValueSet.Fields.VERSION, new BytesRef(internalValueSet.getVersion())));
		addIfNotNull(document, FHIRValueSet.Fields.NAME, internalValueSet.getName());
		addIfNotNull(document, FHIRValueSet.Fields.STATUS, internalValueSet.getStatus());
		if (internalValueSet.getExperimental() != null) {
			document.add(new StringField(FHIRValueSet.Fields.EXPERIMENTAL, internalValueSet.getExperimental() ? "1" : "0", Field.Store.YES));
		}
		addIfNotNull(document, FHIRValueSet.Fields.DESCRIPTION, internalValueSet.getDescription());

		// Store a copy of the whole object in a non-searchable way
		String serialisedVS = objectMapper.writeValueAsString(internalValueSet);
		document.add(new StringField(FHIRValueSet.Fields.SERIALISED, serialisedVS, Field.Store.YES));

		deleteById(internalValueSet.getId());
		indexIOProvider.writeDocument(document);
	}

	private void addIfNotNull(Document document, String fieldName, String value) {
		if (value != null) {
			document.add(new StringField(fieldName, value, Field.Store.YES));
		}
	}

	private FHIRValueSet getVSFromIndex(ScoreDoc scoreDoc, StoredFields storedFields) throws IOException {
		Document document = storedFields.document(scoreDoc.doc);
		String content = document.get(FHIRValueSet.Fields.SERIALISED);
		FHIRValueSet fhirValueSet = objectMapper.readValue(content, FHIRValueSet.class);
		return fhirValueSet;
	}

	public void deleteById(String id) throws IOException {
		indexIOProvider.deleteDocuments(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(CodeSystemRepository.TYPE, FHIRValueSet.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(FHIRValueSet.Fields.ID, id)), BooleanClause.Occur.MUST)
				.build());
	}
}
