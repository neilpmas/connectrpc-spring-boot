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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClientConfigurer;
import org.springframework.util.Assert;

/**
 * Calls a Connect protocol endpoint in tests via {@link WebTestClient}, marshalling
 * requests and responses through a generated stub's {@link MethodDescriptor} directly,
 * rather than requiring per-message-type test code.
 *
 * @author Neil Mason
 */
public class ConnectTestClient {

	private final WebTestClient webTestClient;

	private final String pathPrefix;

	/**
	 * Creates a client wrapping {@code webTestClient}.
	 * @param webTestClient the client to issue requests through
	 * @param pathPrefix the path prefix Connect requests are served under, e.g.
	 * {@code /connect}
	 */
	public ConnectTestClient(WebTestClient webTestClient, String pathPrefix) {
		this.webTestClient = webTestClient;
		this.pathPrefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
	}

	/**
	 * Returns the underlying {@link WebTestClient}, for assertions neither {@link #call}
	 * nor {@link #callExpectingError} covers.
	 * @return the underlying client
	 */
	public WebTestClient webTestClient() {
		return this.webTestClient;
	}

	/**
	 * Returns a new client with {@code configurer} applied, leaving this client
	 * unmodified. Mirrors {@link WebTestClient#mutateWith(WebTestClientConfigurer)}
	 * exactly -- same parameter type, same "returns an independent copy" contract -- so
	 * per-call authentication (e.g. {@code SecurityMockServerConfigurers.mockJwt()},
	 * which implements this interface) composes the same way callers already expect from
	 * raw {@link WebTestClient}.
	 * @param configurer the configurer to apply
	 * @return a new, independently configured client
	 */
	public ConnectTestClient mutateWith(WebTestClientConfigurer configurer) {
		return new ConnectTestClient(this.webTestClient.mutateWith(configurer), this.pathPrefix);
	}

	/**
	 * Calls {@code method} with a protobuf-encoded request, asserting a 2xx response.
	 * @param <ReqT> the request message type
	 * @param <RespT> the response message type
	 * @param method the method to call
	 * @param request the request message
	 * @return the response message
	 */
	public <ReqT, RespT> RespT call(MethodDescriptor<ReqT, RespT> method, ReqT request) {
		return call(method, request, ConnectCodec.PROTO);
	}

	/**
	 * Calls {@code method}, asserting a 2xx response. Only handles the happy path -- use
	 * {@link #callExpectingError} to assert a Connect protocol error response instead, or
	 * {@link #webTestClient()} directly for anything neither covers.
	 * @param <ReqT> the request message type
	 * @param <RespT> the response message type
	 * @param method the method to call
	 * @param request the request message
	 * @param codec the wire codec to marshal the request and response with
	 * @return the response message
	 */
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

	/**
	 * Calls {@code method} with a protobuf-encoded request, asserting a non-2xx response
	 * and parsing it as a Connect protocol error.
	 * @param <ReqT> the request message type
	 * @param <RespT> the response message type
	 * @param method the method to call
	 * @param request the request message
	 * @return the parsed error response
	 */
	public <ReqT, RespT> ConnectError callExpectingError(MethodDescriptor<ReqT, RespT> method, ReqT request) {
		return callExpectingError(method, request, ConnectCodec.PROTO);
	}

	/**
	 * Calls {@code method}, asserting a non-2xx response and parsing it as a Connect
	 * protocol error. Connect protocol errors are always a JSON body
	 * ({@code {"code":"...","message":"..."}}), independent of the request codec, and can
	 * be either 4xx or 5xx (e.g. {@code internal}/{@code unavailable} map to 500/503) --
	 * so this doesn't restrict which non-2xx status is acceptable the way {@link #call}
	 * restricts success to exactly 200.
	 * @param <ReqT> the request message type
	 * @param <RespT> the response message type
	 * @param method the method to call
	 * @param request the request message
	 * @param codec the wire codec to marshal the request with
	 * @return the parsed error response
	 */
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
		HttpStatus httpStatus = HttpStatus.valueOf(result.getStatus().value());
		byte[] responseBytes = result.getResponseBody();
		// A request rejected before it ever reaches ConnectFilter (e.g. Spring Security's
		// own entry point returning a bare 401) has no Connect JSON error body to parse
		// --
		// that's a normal, expected outcome, not a marshalling failure.
		if (responseBytes == null || responseBytes.length == 0) {
			return new ConnectError(httpStatus, null, null);
		}
		Map<String, Object> body = JsonParserFactory.getJsonParser()
			.parseMap(new String(responseBytes, StandardCharsets.UTF_8));
		return new ConnectError(httpStatus, (String) body.get("code"), (String) body.get("message"));
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
