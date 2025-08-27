package org.snomed.snowstormlite.domain.graph;

public class GraphNode {

	private String[] parents;
	private String code;
	private String term;

	public GraphNode(String code, String[] parents, String term) {
		this.code = code;
		this.parents = parents;
		this.term = term;
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

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}
}
