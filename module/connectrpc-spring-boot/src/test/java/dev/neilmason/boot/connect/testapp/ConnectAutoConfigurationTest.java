package dev.neilmason.boot.connect.testapp;

import dev.neilmason.boot.connect.test.greet.v1.SayHelloRequest;
import dev.neilmason.boot.connect.test.greet.v1.SayHelloResponse;
import dev.neilmason.connect.ConnectFilter;
import dev.neilmason.connect.ConnectServiceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
class ConnectAutoConfigurationTest {

    private static final MediaType APPLICATION_PROTO = MediaType.parseMediaType("application/proto");

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void connectFilterAndRegistry_areAutoConfigured() {
        assertThat(applicationContext.getBean(ConnectServiceRegistry.class)).isNotNull();
        assertThat(applicationContext.getBean(ConnectFilter.class)).isNotNull();
    }

    @Test
    void sayHello_isCallableOverConnectProtocol() throws Exception {
        WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

        SayHelloRequest request = SayHelloRequest.newBuilder().setName("World").build();

        byte[] responseBytes = webTestClient
            .post()
            .uri("/connect/greet.v1.GreetService/SayHello")
            .contentType(APPLICATION_PROTO)
            .bodyValue(request.toByteArray())
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

        assertThat(responseBytes).isNotNull();
        assertThat(SayHelloResponse.parseFrom(responseBytes).getGreeting()).isEqualTo("Hello, World!");
    }

    @SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "connect.path-prefix=/custom-prefix"
    )
    static class CustomPathPrefixTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void customPathPrefix_isHonored() throws Exception {
            WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

            SayHelloRequest request = SayHelloRequest.newBuilder().setName("World").build();

            webTestClient
                .post()
                .uri("/custom-prefix/greet.v1.GreetService/SayHello")
                .contentType(APPLICATION_PROTO)
                .bodyValue(request.toByteArray())
                .exchange()
                .expectStatus().isOk();
        }
    }

    @SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "connect.enabled=false"
    )
    static class DisabledTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void whenDisabled_noConnectBeansAreRegistered() {
            assertThat(applicationContext.getBeanNamesForType(ConnectFilter.class)).isEmpty();
            assertThat(applicationContext.getBeanNamesForType(ConnectServiceRegistry.class)).isEmpty();
        }
    }

    @Configuration
    static class CustomFilterConfiguration {

        @Bean
        ConnectFilter connectFilter(ConnectServiceRegistry registry) {
            return new ConnectFilter(registry, "/override", 4_000_000L, true, List.of("*"));
        }
    }

    @SpringBootTest(
        classes = {TestApplication.class, CustomFilterConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
    )
    static class UserSuppliedBeanOverrideTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void userSuppliedConnectFilterBean_takesPrecedence() throws Exception {
            WebTestClient webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();

            SayHelloRequest request = SayHelloRequest.newBuilder().setName("World").build();

            webTestClient
                .post()
                .uri("/override/greet.v1.GreetService/SayHello")
                .contentType(APPLICATION_PROTO)
                .bodyValue(request.toByteArray())
                .exchange()
                .expectStatus().isOk();
        }
    }
}
