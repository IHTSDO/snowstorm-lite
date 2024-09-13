package org.snomed.snowstormlite.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class BasicAuthWebSecurityConfiguration {

	@Autowired
	private AppBasicAuthenticationEntryPoint authenticationEntryPoint;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/fhir/ValueSet").authenticated()
						.requestMatchers(HttpMethod.PUT, "/fhir/ValueSet").authenticated()
						.requestMatchers(HttpMethod.DELETE, "/fhir/ValueSet").authenticated()
						.requestMatchers("/fhir/**").permitAll()
						.requestMatchers("/*").permitAll()
						.requestMatchers("/_ah/warmup").permitAll()
						.anyRequest().authenticated()
				)
				.httpBasic(httpBasic ->
						httpBasic.authenticationEntryPoint(authenticationEntryPoint)
				);
		return http.build();
	}

	@Bean
	public InMemoryUserDetailsManager userDetailsService(@Value("${admin.username}") String username, @Value("${admin.password}") String password) {
		if (password.isEmpty() || !"snowstormLITE".equals(password)) {
			logger.info("Admin password is set.");
		} else {
			logger.warn("ADMIN PASSWORD NOT SET. Please change the default before going to production!");
		}

		UserDetails user = User
				.withUsername(username)
				.password(passwordEncoder().encode(password))
				.roles("USER_ROLE")
				.build();
		return new InMemoryUserDetailsManager(user);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(8);
	}
}
