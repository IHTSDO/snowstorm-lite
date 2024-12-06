package org.snomed.snowstormlite.fhir;

import org.snomed.snowstormlite.domain.graph.GraphNode;
import org.snomed.snowstormlite.service.CodeSystemService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
public class ClosureController {

	private final CodeSystemService codeSystemService;

	public ClosureController(CodeSystemService codeSystemService) {
		this.codeSystemService = codeSystemService;
	}

	@PostMapping("/partial-hierarchy")
	public List<GraphNode> getHierarchyPart(@RequestBody HierarchyRequest hierarchyRequest) throws IOException {
		return codeSystemService.loadHierarchyPart(hierarchyRequest.getSystem(), hierarchyRequest.getVersion(), hierarchyRequest.getCodes());
	}

	public static class HierarchyRequest {

		private String system;
		private String version;
		private List<String> codes;

		public String getSystem() {
			return system;
		}

		public void setSystem(String system) {
			this.system = system;
		}

		public String getVersion() {
			return version;
		}

		public void setVersion(String version) {
			this.version = version;
		}

		public List<String> getCodes() {
			return codes;
		}

		public void setCodes(List<String> codes) {
			this.codes = codes;
		}
	}

}
