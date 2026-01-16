package org.snomed.snowstormlite.mcp.model;

public class SubsumptionResult {
	private String outcome;      // "subsumes", "subsumed-by", "equivalent", "not-subsumed"
	private String codeA;
	private String codeB;
	private String displayA;
	private String displayB;
	private String system;       // "http://snomed.info/sct"
	private String version;

	public SubsumptionResult() {
	}

	private SubsumptionResult(Builder builder) {
		this.outcome = builder.outcome;
		this.codeA = builder.codeA;
		this.codeB = builder.codeB;
		this.displayA = builder.displayA;
		this.displayB = builder.displayB;
		this.system = builder.system;
		this.version = builder.version;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String outcome;
		private String codeA;
		private String codeB;
		private String displayA;
		private String displayB;
		private String system;
		private String version;

		public Builder outcome(String outcome) {
			this.outcome = outcome;
			return this;
		}

		public Builder codeA(String codeA) {
			this.codeA = codeA;
			return this;
		}

		public Builder codeB(String codeB) {
			this.codeB = codeB;
			return this;
		}

		public Builder displayA(String displayA) {
			this.displayA = displayA;
			return this;
		}

		public Builder displayB(String displayB) {
			this.displayB = displayB;
			return this;
		}

		public Builder system(String system) {
			this.system = system;
			return this;
		}

		public Builder version(String version) {
			this.version = version;
			return this;
		}

		public SubsumptionResult build() {
			return new SubsumptionResult(this);
		}
	}

	public String getOutcome() {
		return outcome;
	}

	public void setOutcome(String outcome) {
		this.outcome = outcome;
	}

	public String getCodeA() {
		return codeA;
	}

	public void setCodeA(String codeA) {
		this.codeA = codeA;
	}

	public String getCodeB() {
		return codeB;
	}

	public void setCodeB(String codeB) {
		this.codeB = codeB;
	}

	public String getDisplayA() {
		return displayA;
	}

	public void setDisplayA(String displayA) {
		this.displayA = displayA;
	}

	public String getDisplayB() {
		return displayB;
	}

	public void setDisplayB(String displayB) {
		this.displayB = displayB;
	}

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
}
