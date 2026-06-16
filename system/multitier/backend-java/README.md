# Backend Service

This is the backend API service for the MyShop, built with Java Spring Boot.

## Features

- RESTful API endpoints
- Echo endpoint for health checks
- Todo endpoint that proxies to external API
- CORS enabled for frontend communication

## Endpoints

- `GET /api/echo` - Returns "Echo" for health check
- `GET /api/todos/{id}` - Fetches a todo item by ID


## Instructions

```shell
cd backend
```
Check that you have Powershell 7

```shell
$PSVersionTable.PSVersion
```

Ensure you have JDK 21 installed

```shell
java -version
```

Check that JAVA_HOME is set correctly & points to your JDK 21 installation

```shell
echo $env:JAVA_HOME
```

Ensure you have Gradle 8.14 installed

```shell
./gradlew --version
```

## Building

```shell
./gradlew build
```

## Running Locally

```shell
./gradlew bootRun
```

The service will start on port 8081.

## Docker

Create network:

```shell
docker network create app-network
```

Build the Docker image:

```shell
docker build -t backend .
```

Run the container on the network:

```shell
docker run -d --name backend --network hero-network -p 8081:8081 backend
```

## Optional: in-process component & contract tests

This project ships with an **opt-in** in-process component + Pact contract test
layer that is **deliberately kept off the default build**. `./gradlew build` and
`./gradlew test` run exactly the unit tests they always did — they do **not**
compile or run this layer, and its extra dependencies (WireMock, Pact) are not on
the default `test` classpath.

The layer lives in its own Gradle source set, `src/componentTest/java`, wired via
a `componentTest` task and `componentTestImplementation`/`componentTestRuntimeOnly`
configurations in `build.gradle`.

### Running it

```shell
./gradlew componentTest
```

> **Requires Docker.** The component tests use a real Spring context
> (`RANDOM_PORT`) against a Testcontainers-Postgres database and stub external
> systems with WireMock; the contract test verifies the consumer pact. This Docker
> dependency is part of the documented opt-in cost.

### Removing it

There is intentionally **no generation flag** to exclude this layer — it is
already isolated off the default build, so it is "present but dormant". If you
don't want it, simply ignore it or delete `src/componentTest/` and the
`componentTest` source set / task / configurations from `build.gradle`.

### Contract distribution

The consumer pact is read from the repo-owned `contracts/` folder via
`@PactFolder("../../../contracts")` — a **$0, zero-infra** default (committed pact,
no service). A Pact Broker is a separate, **cost-labelled** opt-in for multi-repo
setups only. See [`contracts/README.md`](../../../contracts/README.md) for the
distribution rule and broker cost details.

## Configuration

Configuration can be modified in `src/main/resources/application.yml`:

- `server.port` - Server port (default: 8081)