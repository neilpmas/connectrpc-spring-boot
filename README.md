# connectrpc-spring-boot

[![CI](https://github.com/neilpmas/connectrpc-spring-boot/actions/workflows/ci.yml/badge.svg)](https://github.com/neilpmas/connectrpc-spring-boot/actions/workflows/ci.yml)

Spring Boot autoconfiguration for [`connectrpc-spring`](https://github.com/neilpmas/connectrpc-spring): add one starter dependency to an existing Spring Boot gRPC application and every `BindableService` bean becomes callable over the [Connect protocol](https://connectrpc.com) — plain HTTP, JSON or proto, no gRPC-Web proxy, no code changes to the service itself.

Autoconfigures the Connect dispatch filter, `@GlobalConnectInterceptor` discovery, Micrometer observability, and the standard gRPC health service, mirroring [`spring-grpc`](https://github.com/spring-projects/spring-grpc)'s own module/starter split.

## Install

```groovy
dependencies {
    implementation 'dev.neilmason:connectrpc-spring-boot-starter:0.2.1'
}
```

Available on [Maven Central](https://central.sonatype.com/artifact/dev.neilmason/connectrpc-spring-boot-starter). A matching [`connectrpc-spring-boot-starter-test`](https://central.sonatype.com/artifact/dev.neilmason/connectrpc-spring-boot-starter-test) adds `ConnectTestClient` and `@AutoConfigureConnectTestClient` for tests.

## Getting Started

The [`gs-connect-rpc`](https://github.com/neilpmas/gs-connect-rpc) guide walks through adding this starter to an existing gRPC service and calling it over HTTP in about 15 minutes.

## License

[Apache License, Version 2.0](LICENSE)
