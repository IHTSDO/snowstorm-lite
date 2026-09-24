package org.snomed.snowstormlite.snomedimport;

import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snomed.snowstormlite.TestConfig;
import org.snomed.snowstormlite.TestService;
import org.snomed.snowstormlite.service.CodeSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfig.class)
class ImportCacheTest {

	@MockitoSpyBean
	private CodeSystemRepository codeSystemRepository;

	@Autowired
	private TestService testService;

	@Test
	void contentLanguagesReadDuringImportAreRefreshedWhenImportCompletes() throws IOException, ReleaseImportException {
		testService.importRF2Int();
		assertEquals(List.of("en"), List.copyOf(codeSystemRepository.getContentLanguageCodes()));

		// Simulate the dashboard reading the CodeSystem while concepts are being written
		doAnswer(invocation -> {
			Object docs = invocation.callRealMethod();
			codeSystemRepository.getContentLanguageCodes();
			return docs;
		}).when(codeSystemRepository).getDocs(anyList());

		testService.importRF2SE();
		assertEquals(List.of("en", "sv"), List.copyOf(codeSystemRepository.getContentLanguageCodes()));
	}

	@AfterEach
	void after() throws IOException {
		testService.tearDown();
	}
}
