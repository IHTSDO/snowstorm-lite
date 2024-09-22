package org.snomed.snowstormlite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@Configuration
public class SyndicationOAuthConfig {

	@Bean
	public OAuth2AuthorizedClientManager authorizedClientManager(OAuth2AuthorizedClientService authorizedClientService,
			ClientRegistrationRepository registrationRepository) {

		OAuth2AuthorizedClientProvider authorizedClientProvider =
				OAuth2AuthorizedClientProviderBuilder.builder()
						.clientCredentials()
						.build();

		DefaultOAuth2AuthorizedClientManager authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(
				registrationRepository,
				new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService));

		authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

		return authorizedClientManager;
	}

	@Bean
	public OAuth2AuthorizedClientService authorizedClientService(ClientRegistrationRepository registrationRepository) {
		return new InMemoryOAuth2AuthorizedClientService(registrationRepository);
	}

	@Bean
	public ClientRegistrationRepository clientRegistrationRepository(
			@Value("${spring.security.oauth2.client.registration.syndication-client.client-id}") String clientId,
			@Value("${spring.security.oauth2.client.registration.syndication-client.client-secret}") String clientSecret,
			@Value("${spring.security.oauth2.client.provider.syndication-provider.token-uri}") String providerTokenURI) {

		ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("syndication-client")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.tokenUri(providerTokenURI)
				.build();
		return new InMemoryClientRegistrationRepository(clientRegistration);
	}

}
