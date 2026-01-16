package org.snomed.snowstormlite.mcp.model;

public class ValidationResult {
	private boolean exists;
	private boolean active;
	private String message;
	private String conceptId;
	private String display;

	public ValidationResult() {
	}

	private ValidationResult(Builder builder) {
		this.exists = builder.exists;
		this.active = builder.active;
		this.message = builder.message;
		this.conceptId = builder.conceptId;
		this.display = builder.display;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private boolean exists;
		private boolean active;
		private String message;
		private String conceptId;
		private String display;

		public Builder exists(boolean exists) {
			this.exists = exists;
			return this;
		}

		public Builder active(boolean active) {
			this.active = active;
			return this;
		}

		public Builder message(String message) {
			this.message = message;
			return this;
		}

		public Builder conceptId(String conceptId) {
			this.conceptId = conceptId;
			return this;
		}

		public Builder display(String display) {
			this.display = display;
			return this;
		}

		public ValidationResult build() {
			return new ValidationResult(this);
		}
	}

	public boolean isExists() {
		return exists;
	}

	public void setExists(boolean exists) {
		this.exists = exists;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
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
}
