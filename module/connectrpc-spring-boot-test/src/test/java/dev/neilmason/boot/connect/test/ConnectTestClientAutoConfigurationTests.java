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
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClientConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureConnectTestClient
class ConnectTestClientAutoConfigurationTests {

	@Autowired
	private ConnectTestClient connectTestClient;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void connectTestClientAndWebTestClient_areBothAutoConfigured() {
		assertThat(this.connectTestClient).isNotNull();
		assertThat(this.webTestClient).isNotNull();
	}

	@Test
	void call_marshalsAndUnmarshalsProtoByDefault() {
		SayHelloRequest request = SayHelloRequest.newBuilder().setName("World").build();

		SayHelloResponse response = this.connectTestClient.call(GreetServiceGrpc.getSayHelloMethod(), request);

		assertThat(response.getGreeting()).isEqualTo("Hello, World!");
	}

	@Test
	void call_supportsJsonCodec() {
		SayHelloRequest request = SayHelloRequest.newBuilder().setName("JSON").build();

		SayHelloResponse response = this.connectTestClient.call(GreetServiceGrpc.getSayHelloMethod(), request,
				ConnectCodec.JSON);

		assertThat(response.getGreeting()).isEqualTo("Hello, JSON!");
	}

	@Test
	void webTestClient_returnsTheSameAutoconfiguredInstance() {
		assertThat(this.connectTestClient.webTestClient()).isSameAs(this.webTestClient);
	}

	@Test
	void mutateWith_returnsAnIndependentClientThatStillWorks() {
		WebTestClientConfigurer addHeader = (builder, httpHandlerBuilder, connector) -> builder
			.defaultHeader("X-Test-Mutation", "applied");

		ConnectTestClient mutated = this.connectTestClient.mutateWith(addHeader);
		assertThat(mutated).isNotSameAs(this.connectTestClient);

		SayHelloRequest request = SayHelloRequest.newBuilder().setName("Mutated").build();

		SayHelloResponse mutatedResponse = mutated.call(GreetServiceGrpc.getSayHelloMethod(), request);
		assertThat(mutatedResponse.getGreeting()).isEqualTo("Hello, Mutated!");

		// The original client is unaffected by the mutation -- still works normally.
		SayHelloResponse originalResponse = this.connectTestClient.call(GreetServiceGrpc.getSayHelloMethod(), request);
		assertThat(originalResponse.getGreeting()).isEqualTo("Hello, Mutated!");
	}

	@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
			properties = "connect.path-prefix=/custom-prefix")
	@AutoConfigureConnectTestClient
	static class CustomPathPrefixTests {

		@Autowired
		private ConnectTestClient connectTestClient;

		@Test
		void call_honorsConfiguredPathPrefix() {
			SayHelloRequest request = SayHelloRequest.newBuilder().setName("World").build();

			SayHelloResponse response = this.connectTestClient.call(GreetServiceGrpc.getSayHelloMethod(), request);

			assertThat(response.getGreeting()).isEqualTo("Hello, World!");
		}

	}

}
