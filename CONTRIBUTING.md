# Contributing

## Setup

`connectrpc-spring-boot` depends on `connectrpc-spring-core`, which isn't published anywhere yet — build and install it locally first:

```sh
git clone https://github.com/neilpmas/connectrpc-spring.git
cd connectrpc-spring
./mvnw install -DskipTests
```

Then:

```sh
git clone https://github.com/neilpmas/connectrpc-spring-boot.git
cd connectrpc-spring-boot
./gradlew build
```

## Development

```sh
./gradlew test     # run tests
./gradlew build    # full build, including checkstyle
./gradlew format   # auto-fix formatting violations
```

## Submitting a PR

1. Fork the repo
2. Create a branch: `git checkout -b my-fix`
3. Make your changes — tests must pass and checkstyle must be clean
4. Open a pull request against `main`
