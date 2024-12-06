package org.snomed.snowstormlite.domain.graph;

public class GraphNode {

	private String[] parents;
	private String code;

	public GraphNode(String code, String[] parents) {
		this.code = code;
		this.parents = parents;
	}

	public String[] getParents() {
		return parents;
	}

	public void setParents(String[] parents) {
		this.parents = parents;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}
}
