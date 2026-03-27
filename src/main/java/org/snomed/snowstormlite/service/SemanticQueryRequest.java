package org.snomed.snowstormlite.service;

public class SemanticQueryRequest {

	private final boolean enabled;
	private final String modelId;
	private final float[] vector;

	private SemanticQueryRequest(boolean enabled, String modelId, float[] vector) {
		this.enabled = enabled;
		this.modelId = modelId;
		this.vector = vector;
	}

	public static SemanticQueryRequest disabled() {
		return new SemanticQueryRequest(false, null, null);
	}

	public static SemanticQueryRequest enabled(String modelId, float[] vector) {
		return new SemanticQueryRequest(true, modelId, vector);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getModelId() {
		return modelId;
	}

	public float[] getVector() {
		return vector;
	}
}
