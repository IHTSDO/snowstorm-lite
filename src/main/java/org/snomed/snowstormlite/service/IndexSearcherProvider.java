package org.snomed.snowstormlite.service;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.NIOFSDirectory;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.snomed.snowstormlite.fhir.FHIRHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class IndexSearcherProvider {

	private IndexSearcher indexSearcher;

	@Value("${index.path}")
	private String indexPath;

	public IndexSearcher getIndexSearcher() {
		if (indexSearcher == null) {
			throw FHIRHelper.exception("SNOMED CT has not yet been loaded.", OperationOutcome.IssueType.CONFLICT, 409);
		}
		return indexSearcher;
	}

	public void createIndexSearcher() throws IOException {
		indexSearcher = new IndexSearcher(DirectoryReader.open(new NIOFSDirectory(new File(indexPath).toPath())));
	}

	public void clearIndexSearcher() {
		indexSearcher = null;
	}
}
