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

package dev.neilmason.boot.connect.testapp;

import java.util.List;

import dev.neilmason.boot.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.boot.connect.test.greet.v1.SayHelloResponse;
import dev.neilmason.connect.ConnectFilter;
import dev.neilmason.connect.ConnectServiceRegistry;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ConnectAutoConfigurationTests {

	private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void connectFilterAndRegistry_areAutoConfigured() {
		assertThat(this.applicationContext.getBean(ConnectServiceRegistry.class)).isNotNull();
		assertThat(this.applicationContext.getBean(ConnectFilter.class)).isNotNull();
	}

	@Test
	void sayHello_isCallableOverConnectProtocol() throws Exception {
		WebTestClient webTestClient = WebTestClient.bindToApplicationContext(this.applicationContext).build();

		SayHelloRequest request = SayHelloRequest.newBuilder().setName("World").build();

		byte[] responseBytes = webTestClient.post()
			.uri("/connect/greet.v1.GreetService/SayHello")
			.contentType(APPLICATION_PROTO)
			.bodyValue(request.toByteArray())
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(byte[].class)
			.returnResult()
			.getResponseBody();

		assertThat(responseBytes).isNotNull();
		assertThat(SayHelloResponse.parseFrom(responseBytes).getGreeting()).isEqualTo("Hello, World!");
	}

	@Test
	void healthCheck_isCallableOverConnectProtocol() throws Exception {
		WebTestClient webTestClient = WebTestClient.bindToApplicationContext(this.applicationContext).build();

		HealthCheckRequest request = HealthCheckRequest.newBuilder().build();

		byte[] responseBytes = webTestClient.post()
			.uri("/connect/grpc.health.v1.Health/Check")
			.contentType(APPLICATION_PROTO)
			.bodyValue(request.toByteArray())
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(byte[].class)
			.returnResult()
			.getResponseBody();

		assertThat(responseBytes).isNotNull();
		assertThat(HealthCheckResponse.parseFrom(responseBytes).getStatus())
			.isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
	}

	@Test
	void healthStatusManager_isAutoConfigured() {
		assertThat(this.applicationContext.getBean(HealthStatusManager.class)).isNotNull();
	}

	@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
			properties = "connect.path-prefix=/custom-prefix")
	static class CustomPathPrefixTest {

		@Autowired
		private ApplicationContext applicationContext;

		@Test
		void customPathPrefix_isHonored() {
			WebTestClient webTestClient = WebTestClient.bindToApplicationContext(this.applicationContext).build();

			SayHelloRequest request = SayHelloRequest.newBuilder().setName("World").build();

			webTestClient.post()
				.uri("/custom-prefix/greet.v1.GreetService/SayHello")
				.contentType(APPLICATION_PROTO)
				.bodyValue(request.toByteArray())
				.exchange()
				.expectStatus()
				.isOk();
		}

	}

	@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
			properties = "connect.enabled=false")
	static class DisabledTest {

		@Autowired
		private ApplicationContext applicationContext;

		@Test
		void whenDisabled_noConnectBeansAreRegistered() {
			assertThat(this.applicationContext.getBeanNamesForType(ConnectFilter.class)).isEmpty();
			assertThat(this.applicationContext.getBeanNamesForType(ConnectServiceRegistry.class)).isEmpty();
		}

	}

	@Configuration
	static class ObservationRegistryConfiguration {

		@Bean
		ObservationRegistry observationRegistry() {
			return ObservationRegistry.create();
		}

	}

	@SpringBootTest(classes = { TestApplication.class, ObservationRegistryConfiguration.class },
			webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	static class ObservationTest {

		@Autowired
		private ApplicationContext applicationContext;

		@Test
		void observationInterceptor_isAutoConfiguredAsGlobalInterceptor() {
			ObservationGrpcServerInterceptor interceptor = this.applicationContext
				.getBean(ObservationGrpcServerInterceptor.class);
			assertThat(interceptor).isNotNull();
			assertThat(this.applicationContext.findAnnotationOnBean(
					this.applicationContext.getBeanNamesForType(ObservationGrpcServerInterceptor.class)[0],
					dev.neilmason.connect.GlobalConnectInterceptor.class))
				.isNotNull();
		}

	}

	@Configuration
	static class CustomFilterConfiguration {

		@Bean
		ConnectFilter connectFilter(ConnectServiceRegistry registry) {
			return new ConnectFilter(registry, "/override", 4_000_000L, true, List.of("*"));
		}

	}

	@SpringBootTest(classes = { TestApplication.class, CustomFilterConfiguration.class },
			webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	static class UserSuppliedBeanOverrideTest {

		@Autowired
		private ApplicationContext applicationContext;

		@Test
		void userSuppliedConnectFilterBean_takesPrecedence() {
			WebTestClient webTestClient = WebTestClient.bindToApplicationContext(this.applicationContext).build();

			SayHelloRequest request = SayHelloRequest.newBuilder().setName("World").build();

			webTestClient.post()
				.uri("/override/greet.v1.GreetService/SayHello")
				.contentType(APPLICATION_PROTO)
				.bodyValue(request.toByteArray())
				.exchange()
				.expectStatus()
				.isOk();
		}

	}

}
