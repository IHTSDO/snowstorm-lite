package org.snomed.snowstormlite.mcp.model;

import java.util.List;

public class ConceptSearchResult {
	private int totalResults;
	private int limit;
	private List<ConceptSummary> concepts;

	public ConceptSearchResult() {
	}

	private ConceptSearchResult(Builder builder) {
		this.totalResults = builder.totalResults;
		this.limit = builder.limit;
		this.concepts = builder.concepts;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private int totalResults;
		private int limit;
		private List<ConceptSummary> concepts;

		public Builder totalResults(int totalResults) {
			this.totalResults = totalResults;
			return this;
		}

		public Builder limit(int limit) {
			this.limit = limit;
			return this;
		}

		public Builder concepts(List<ConceptSummary> concepts) {
			this.concepts = concepts;
			return this;
		}

		public ConceptSearchResult build() {
			return new ConceptSearchResult(this);
		}
	}

	public static class ConceptSummary {
		private String conceptId;
		private String display;
		private boolean active;
		private String matchedTerm;

		public ConceptSummary() {
		}

		private ConceptSummary(Builder builder) {
			this.conceptId = builder.conceptId;
			this.display = builder.display;
			this.active = builder.active;
			this.matchedTerm = builder.matchedTerm;
		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {
			private String conceptId;
			private String display;
			private boolean active;
			private String matchedTerm;

			public Builder conceptId(String conceptId) {
				this.conceptId = conceptId;
				return this;
			}

			public Builder display(String display) {
				this.display = display;
				return this;
			}

			public Builder active(boolean active) {
				this.active = active;
				return this;
			}

			public Builder matchedTerm(String matchedTerm) {
				this.matchedTerm = matchedTerm;
				return this;
			}

			public ConceptSummary build() {
				return new ConceptSummary(this);
			}
		}

		public String getConceptId() {
			return conceptId;
		}

		public void setConceptId(String conceptId) {
			this.conceptId = conceptId;
		}

		public String getDisplay() {
			return display;
		}

		public void setDisplay(String display) {
			this.display = display;
		}

		public boolean isActive() {
			return active;
		}

		public void setActive(boolean active) {
			this.active = active;
		}

		public String getMatchedTerm() {
			return matchedTerm;
		}

		public void setMatchedTerm(String matchedTerm) {
			this.matchedTerm = matchedTerm;
		}
	}

	public int getTotalResults() {
		return totalResults;
	}

	public void setTotalResults(int totalResults) {
		this.totalResults = totalResults;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public List<ConceptSummary> getConcepts() {
		return concepts;
	}

	public void setConcepts(List<ConceptSummary> concepts) {
		this.concepts = concepts;
	}
}
