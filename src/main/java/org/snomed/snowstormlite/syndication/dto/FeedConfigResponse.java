package org.snomed.snowstormlite.syndication.dto;

public record FeedConfigResponse(String url, String username, String defaultUrl, boolean passwordSet) {
}
