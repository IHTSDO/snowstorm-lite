package org.snomed.snowstormlite.syndication;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import java.io.IOException;

public class OAuth2RestTemplateInterceptor implements ClientHttpRequestInterceptor {

	private final OAuth2AuthorizedClientService authorizedClientService;
	private final String clientRegistrationId;

	public OAuth2RestTemplateInterceptor(OAuth2AuthorizedClientService oAuth2AuthorizedClientService, String clientRegistrationId) {
		this.authorizedClientService = oAuth2AuthorizedClientService;
		this.clientRegistrationId = clientRegistrationId;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
		OAuth2AuthorizedClient authorizedClient = this.authorizedClientService.loadAuthorizedClient(
				this.clientRegistrationId,
				"snowstorm-lite-client"
		);

		if (authorizedClient != null) {
			String token = authorizedClient.getAccessToken().getTokenValue();
			request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}

		return execution.execute(request, body);
	}
}
