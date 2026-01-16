package org.snomed.snowstormlite.mcp.exception;

public class McpException extends RuntimeException {
	private final String errorCode;
	private final int statusCode;

	public McpException(String message, String errorCode, int statusCode) {
		super(message);
		this.errorCode = errorCode;
		this.statusCode = statusCode;
	}

	public McpException(String message, String errorCode, int statusCode, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
		this.statusCode = statusCode;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public int getStatusCode() {
		return statusCode;
	}
}
