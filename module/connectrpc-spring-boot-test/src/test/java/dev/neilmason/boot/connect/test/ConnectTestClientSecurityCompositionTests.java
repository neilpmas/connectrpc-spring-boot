/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.neilmason.boot.connect.test;

import dev.neilmason.boot.connect.test.testapp.TestApplication;
import dev.neilmason.boot.connect.test.testapp.greet.v1.GreetServiceGrpc;
import dev.neilmason.boot.connect.test.testapp.greet.v1.SayHelloRequest;
import dev.neilmason.boot.connect.test.testapp.greet.v1.SayHelloResponse;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.MockServerConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

// Reproduces the scenario from https://github.com/neilmason/connectrpc-spring-boot/issues/9:
// does registering SecurityMockServerConfigurers.springSecurity() as a MockServerConfigurer
// bean actually get applied? Spring Boot's own WebTestClientAutoConfiguration.webTestClient(...)
// takes a List<MockServerConfigurer> and applies each one via MockServerSpec.apply(...) before
// ConnectTestClientAutoConfiguration ever sees the resulting WebTestClient -- this isolated test
// app (not the shared TestApplication other tests use) proves that composition path works.
@SpringBootTest(classes = { TestApplication.class, ConnectTestClientSecurityCompositionTests.SecurityTestConfig.class },
		webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureConnectTestClient
class ConnectTestClientSecurityCompositionTests {

	@Autowired
	private ConnectTestClient connectTestClient;

	@Test
	void authenticatedCall_succeedsWithSpringSecurityAndMockJwtComposed() {
		SayHelloRequest request = SayHelloRequest.newBuilder().setName("Secured").build();

		SayHelloResponse response = this.connectTestClient
			.mutateWith(SecurityMockServerConfigurers.mockJwt().jwt((jwt) -> jwt.subject("test-user")))
			.call(GreetServiceGrpc.getSayHelloMethod(), request);

		assertThat(response.getGreeting()).isEqualTo("Hello, Secured!");
	}

	@Test
	void unauthenticatedCall_isRejected() {
		SayHelloRequest request = SayHelloRequest.newBuilder().setName("Secured").build();

		this.connectTestClient.webTestClient()
			.post()
			.uri("/connect/greet.v1.GreetService/SayHello")
			.contentType(MediaType.parseMediaType("application/proto"))
			.bodyValue(request.toByteArray())
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	@TestConfiguration(proxyBeanMethods = false)
	@EnableWebFluxSecurity
	static class SecurityTestConfig {

		@Bean
		SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
			return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange((exchanges) -> exchanges.anyExchange().authenticated())
				.build();
		}

		@Bean
		MockServerConfigurer springSecurityConfigurer() {
			return SecurityMockServerConfigurers.springSecurity();
		}

	}

}
