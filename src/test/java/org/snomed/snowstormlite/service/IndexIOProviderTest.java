package org.snomed.snowstormlite.service;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IndexIOProviderTest {

	@TempDir
	Path indexDir;

	@Test
	void replacedReadersAreClosed() throws Exception {
		IndexIOProvider provider = new IndexIOProvider(indexDir.toString(), 0);
		try {
			provider.writeDocument(doc("1"));
			provider.enableRead();
			IndexReader first = provider.getIndexSearcher().getIndexReader();

			provider.writeDocument(doc("2"));
			IndexSearcher current = provider.getIndexSearcher();
			assertNotSame(first, current.getIndexReader());
			assertEquals(2, current.count(new MatchAllDocsQuery()));

			provider.deleteDocuments(new MatchAllDocsQuery());
			waitForClose(first);
			waitForClose(current.getIndexReader());
			assertEquals(0, first.getRefCount(), "replaced reader should be closed");
			assertEquals(0, current.getIndexReader().getRefCount(), "replaced reader should be closed");
			assertEquals(0, provider.getIndexSearcher().count(new MatchAllDocsQuery()));
		} finally {
			provider.close();
		}
	}

	private static void waitForClose(IndexReader reader) throws InterruptedException {
		for (int i = 0; i < 50 && reader.getRefCount() > 0; i++) {
			Thread.sleep(20);
		}
	}

	private static Document doc(String id) {
		Document document = new Document();
		document.add(new StringField("id", id, Field.Store.YES));
		return document;
	}
}
