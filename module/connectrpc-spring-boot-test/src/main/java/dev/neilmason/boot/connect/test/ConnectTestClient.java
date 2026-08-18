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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.MethodDescriptor;

import org.springframework.boot.json.JsonParserFactory;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClientConfigurer;
import org.springframework.util.Assert;

public class ConnectTestClient {

	private final WebTestClient webTestClient;

	private final String pathPrefix;

	public ConnectTestClient(WebTestClient webTestClient, String pathPrefix) {
		this.webTestClient = webTestClient;
		this.pathPrefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
	}

	public WebTestClient webTestClient() {
		return this.webTestClient;
	}

	// Mirrors WebTestClient's own mutateWith(WebTestClientConfigurer) exactly -- same
	// parameter type, same "returns an independent copy" contract -- so per-call
	// authentication (e.g. SecurityMockServerConfigurers.mockJwt(), which implements this
	// interface) composes the same way callers already expect from raw WebTestClient.
	public ConnectTestClient mutateWith(WebTestClientConfigurer configurer) {
		return new ConnectTestClient(this.webTestClient.mutateWith(configurer), this.pathPrefix);
	}

	// call(...) only handles the 2xx happy path -- use callExpectingError(...) to assert
	// a
	// Connect protocol error response instead, or webTestClient() directly for anything
	// neither covers.
	public <ReqT, RespT> RespT call(MethodDescriptor<ReqT, RespT> method, ReqT request) {
		return call(method, request, ConnectCodec.PROTO);
	}

	public <ReqT, RespT> RespT call(MethodDescriptor<ReqT, RespT> method, ReqT request, ConnectCodec codec) {
		byte[] requestBytes = marshalRequest(method, request, codec);
		byte[] responseBytes = this.webTestClient.post()
			.uri(this.pathPrefix + method.getFullMethodName())
			.contentType(codec.mediaType())
			.bodyValue(requestBytes)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(byte[].class)
			.returnResult()
			.getResponseBody();
		Assert.state(responseBytes != null, "No response body");
		return unmarshalResponse(method, responseBytes, codec);
	}

	// Connect protocol errors are always a JSON body ({"code":"...","message":"..."}),
	// independent of the request codec, and can be either 4xx or 5xx (e.g. "internal"/
	// "unavailable" map to 500/503) -- so this doesn't restrict which non-2xx status is
	// acceptable the way call() restricts success to exactly 200.
	public <ReqT, RespT> ConnectError callExpectingError(MethodDescriptor<ReqT, RespT> method, ReqT request) {
		return callExpectingError(method, request, ConnectCodec.PROTO);
	}

	public <ReqT, RespT> ConnectError callExpectingError(MethodDescriptor<ReqT, RespT> method, ReqT request,
			ConnectCodec codec) {
		byte[] requestBytes = marshalRequest(method, request, codec);
		EntityExchangeResult<byte[]> result = this.webTestClient.post()
			.uri(this.pathPrefix + method.getFullMethodName())
			.contentType(codec.mediaType())
			.bodyValue(requestBytes)
			.exchange()
			.expectBody(byte[].class)
			.returnResult();
		Assert.state(!result.getStatus().is2xxSuccessful(),
				() -> "Expected a Connect protocol error response, but got " + result.getStatus());
		byte[] responseBytes = result.getResponseBody();
		Assert.state(responseBytes != null, "No response body");
		Map<String, Object> body = JsonParserFactory.getJsonParser()
			.parseMap(new String(responseBytes, StandardCharsets.UTF_8));
		return new ConnectError((String) body.get("code"), (String) body.get("message"));
	}

	private <ReqT, RespT> byte[] marshalRequest(MethodDescriptor<ReqT, RespT> method, ReqT request,
			ConnectCodec codec) {
		try {
			return switch (codec) {
				case PROTO -> readAllBytes(method.streamRequest(request));
				case JSON -> JsonFormat.printer().print((Message) request).getBytes(StandardCharsets.UTF_8);
			};
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to marshal Connect request", ex);
		}
	}

	private byte[] readAllBytes(InputStream stream) throws IOException {
		try (stream) {
			return stream.readAllBytes();
		}
	}

	@SuppressWarnings("unchecked")
	private <ReqT, RespT> RespT unmarshalResponse(MethodDescriptor<ReqT, RespT> method, byte[] responseBytes,
			ConnectCodec codec) {
		try {
			return switch (codec) {
				case PROTO -> method.parseResponse(new ByteArrayInputStream(responseBytes));
				case JSON -> (RespT) parseJsonResponse(method, responseBytes);
			};
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to unmarshal Connect response", ex);
		}
	}

	// Protobuf-based marshallers (the only kind this library supports) implement
	// PrototypeMarshaller, exposing the default instance of the concrete Message type for
	// JSON parsing -- the client-side mirror of ConnectFilter's server-side equivalent.
	@SuppressWarnings("DataFlowIssue") // PrototypeMarshaller contract guarantees a
										// non-null prototype
	private Message parseJsonResponse(MethodDescriptor<?, ?> method, byte[] responseBytes) throws IOException {
		MethodDescriptor.PrototypeMarshaller<?> marshaller = (MethodDescriptor.PrototypeMarshaller<?>) method
			.getResponseMarshaller();
		Message prototype = (Message) marshaller.getMessagePrototype();
		Message.Builder builder = prototype.newBuilderForType();
		JsonFormat.parser().merge(new String(responseBytes, StandardCharsets.UTF_8), builder);
		return builder.build();
	}

}
