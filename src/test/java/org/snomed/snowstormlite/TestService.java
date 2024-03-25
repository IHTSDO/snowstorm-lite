package org.snomed.snowstormlite;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.snomed.otf.snomedboot.testutil.ZipUtil;
import org.snomed.snowstormlite.domain.LanguageDialect;
import org.snomed.snowstormlite.service.AppSetupService;
import org.snomed.snowstormlite.service.IndexIOProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class TestService {

	@Autowired
	private AppSetupService appSetupService;

	@Autowired
	private IndexIOProvider indexIOProvider;

	public static final List<LanguageDialect> EN_LANGUAGE_DIALECTS = List.of(new LanguageDialect("en", 900000000000509007L));

	public void importRF2Int() throws IOException, ReleaseImportException {
		importRF2(List.of(
				"src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2/Snapshot"
		), "http://snomed.info/sct/900000000000207008/version/20240101");
	}

	public void importRF2SE() throws IOException, ReleaseImportException {
		importRF2(List.of(
				"src/test/resources/dummy-snomed-content/SnomedCT_MiniRF2/Snapshot",
				"src/test/resources/dummy-snomed-content/SnomedCT_SE_MiniRF2/Snapshot"
		), "http://snomed.info/sct/45991000052106/version/20240115");
	}

	public void importRF2(List<String> paths, String url) throws IOException, ReleaseImportException {
		List<String> zipPaths = new ArrayList<>();
		for (String path : paths) {
			File zipFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines(path);
			zipPaths.add(zipFile.getAbsolutePath());
		}
		appSetupService.setLoadReleaseArchives(String.join(",", zipPaths));
		appSetupService.setLoadVersionUri(url);
		appSetupService.run();
	}

	public void tearDown() throws IOException {
		indexIOProvider.deleteDocuments(new MatchAllDocsQuery());
	}
}
