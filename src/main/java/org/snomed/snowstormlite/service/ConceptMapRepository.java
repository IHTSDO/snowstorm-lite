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
import org.snomed.snowstormlite.domain.conceptmap.FHIRConceptMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConceptMapRepository {

	@Autowired
	private IndexIOProvider indexIOProvider;

	@Autowired
	private ObjectMapper objectMapper;

	public FHIRConceptMap findConceptMap(String url, String version) throws IOException {
		if (url == null) {
			return null;
		}
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		BooleanQuery.Builder builder = new BooleanQuery.Builder()
				.add(new TermQuery(new Term(CodeSystemRepository.TYPE, FHIRConceptMap.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(FHIRConceptMap.Fields.URL, url)), BooleanClause.Occur.MUST);
		if (version != null) {
			builder.add(new TermQuery(new Term(FHIRConceptMap.Fields.VERSION, version)), BooleanClause.Occur.MUST);
		}
		TopDocs topDocs = indexSearcher.search(builder.build(), 1,
				new Sort(new SortField(FHIRConceptMap.Fields.VERSION, SortField.Type.STRING, true)));
		if (topDocs.totalHits.value > 0) {
			return getFromIndex(topDocs.scoreDocs[0], indexSearcher.storedFields());
		}
		return null;
	}

	public FHIRConceptMap findConceptMapById(String id) throws IOException {
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		TopDocs topDocs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(CodeSystemRepository.TYPE, FHIRConceptMap.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(FHIRConceptMap.Fields.ID, id)), BooleanClause.Occur.MUST)
				.build(), 1);
		if (topDocs.totalHits.value > 0) {
			return getFromIndex(topDocs.scoreDocs[0], indexSearcher.storedFields());
		}
		return null;
	}

	public List<FHIRConceptMap> findAll() throws IOException {
		IndexSearcher indexSearcher = indexIOProvider.getIndexSearcher();
		TopDocs topDocs = indexSearcher.search(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(CodeSystemRepository.TYPE, FHIRConceptMap.DOC_TYPE)), BooleanClause.Occur.MUST)
				.build(),
				10_000, new Sort(new SortField(FHIRConceptMap.Fields.URL, SortField.Type.DOC), new SortField(FHIRConceptMap.Fields.VERSION, SortField.Type.DOC, true)));

		List<FHIRConceptMap> all = new ArrayList<>();
		StoredFields storedFields = indexSearcher.getIndexReader().storedFields();
		for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
			all.add(getFromIndex(scoreDoc, storedFields));
		}
		return all;
	}

	public void save(FHIRConceptMap conceptMap) throws IOException {
		Document document = new Document();
		document.add(new StringField(CodeSystemRepository.TYPE, FHIRConceptMap.DOC_TYPE, Field.Store.YES));
		document.add(new StringField(FHIRConceptMap.Fields.ID, conceptMap.getId(), Field.Store.YES));
		document.add(new StringField(FHIRConceptMap.Fields.URL, conceptMap.getUrl(), Field.Store.YES));
		document.add(new SortedDocValuesField(FHIRConceptMap.Fields.URL, new BytesRef(conceptMap.getUrl())));
		document.add(new StringField(FHIRConceptMap.Fields.VERSION, conceptMap.getVersion(), Field.Store.YES));
		document.add(new SortedDocValuesField(FHIRConceptMap.Fields.VERSION, new BytesRef(conceptMap.getVersion())));
		String serialised = objectMapper.writeValueAsString(conceptMap);
		document.add(new StringField(FHIRConceptMap.Fields.SERIALISED, serialised, Field.Store.YES));

		deleteById(conceptMap.getId());
		indexIOProvider.writeDocument(document);
	}

	private FHIRConceptMap getFromIndex(ScoreDoc scoreDoc, StoredFields storedFields) throws IOException {
		Document document = storedFields.document(scoreDoc.doc);
		String content = document.get(FHIRConceptMap.Fields.SERIALISED);
		return objectMapper.readValue(content, FHIRConceptMap.class);
	}

	public void deleteById(String id) throws IOException {
		indexIOProvider.deleteDocuments(new BooleanQuery.Builder()
				.add(new TermQuery(new Term(CodeSystemRepository.TYPE, FHIRConceptMap.DOC_TYPE)), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term(FHIRConceptMap.Fields.ID, id)), BooleanClause.Occur.MUST)
				.build());
	}
}
