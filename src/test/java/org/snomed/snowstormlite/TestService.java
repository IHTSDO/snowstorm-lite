package org.snomed.snowstormlite;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstormlite.service.AppSetupService;
import org.snomed.snowstormlite.service.IndexIOProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class TestService {

	@Autowired
	private AppSetupService appSetupService;

	@Autowired
	private IndexIOProvider indexIOProvider;

	public void importRF2() throws IOException, ReleaseImportException {
		String pathname = "src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2/Snapshot";
		File zipFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines(pathname);
		appSetupService.setLoadReleaseArchives(zipFile.getAbsolutePath());
		appSetupService.setLoadVersionUri("http://snomed.info/sct/900000000000207008/version/20200731");
		appSetupService.run();
	}

	public void tearDown() throws IOException {
		indexIOProvider.deleteDocuments(new MatchAllDocsQuery());
	}
}
