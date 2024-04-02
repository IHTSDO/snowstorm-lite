package org.snomed.snowstormlite.snomedimport;

import com.google.common.collect.Lists;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstormlite.domain.FHIRCodeSystem;
import org.snomed.snowstormlite.domain.FHIRConcept;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.snomed.snowstormlite.service.IndexIOProvider;
import org.snomed.snowstormlite.service.QueryHelper;

import java.io.IOException;
import java.util.List;

public class IndexCreator implements AutoCloseable {

	private final CodeSystemRepository codeSystemRepository;
	private final IndexIOProvider indexIOProvider;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public IndexCreator(IndexIOProvider indexIOProvider, CodeSystemRepository codeSystemRepository) {
		this.codeSystemRepository = codeSystemRepository;
		this.indexIOProvider = indexIOProvider;
		indexIOProvider.disableRead();
	}

	public void createCodeSystem(String versionUri) throws IOException {
		codeSystemRepository.clearCache();
		// Delete CodeSystem and all concepts (includes implicit ValueSets but not FHIR native ValueSets)
		indexIOProvider.deleteDocuments(new BooleanQuery.Builder()
				.add(QueryHelper.termsQuery(CodeSystemRepository.TYPE, List.of(FHIRCodeSystem.DOC_TYPE, FHIRConcept.DOC_TYPE)), BooleanClause.Occur.MUST).build());
		Document codeSystemDoc = codeSystemRepository.getCodeSystemDoc(versionUri);
		indexIOProvider.writeDocument(codeSystemDoc);
	}

	public void createConceptBatch(List<FHIRConcept> conceptBatch) throws IOException {
		logger.debug("Writing batch of {} concepts.", conceptBatch.size());
		for (List<FHIRConcept> conceptWriteBatch : Lists.partition(conceptBatch, 10_000)) {
			List<Document> conceptDocs = codeSystemRepository.getDocs(conceptWriteBatch);
			indexIOProvider.writeDocuments(conceptDocs);
			System.out.print(".");
		}
		System.out.println();
	}

	@Override
	public void close() throws IOException {
		indexIOProvider.enableRead();
	}
}
