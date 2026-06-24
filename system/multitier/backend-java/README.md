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

## Component tests

This project ships with an in-process component + Pact contract test layer that is
**deliberately kept off the default build**. `./gradlew build` and `./gradlew test`
run exactly the unit tests — they do **not** compile or run this layer.

The layer lives in its own Gradle source set, `src/componentTest/java`, wired via
a `componentTest` task and `componentTestImplementation`/`componentTestRuntimeOnly`
configurations in `build.gradle`.

### Running via gh optivem (recommended — matches CI)

```shell
# All suites across all components (matches the CI gate exactly)
gh optivem component-test run

# Single suite (fast inner loop)
gh optivem component-test run --suite unit
gh optivem component-test run --suite component --component backend
gh optivem component-test run --suite contract  --component backend

# One-time setup (pre-warm Gradle)
gh optivem component-test setup --component backend
```

> **Requires Docker** for the `component` and `contract` suites (Testcontainers-Postgres + WireMock). `--suite unit` is Docker-free.

### Running natively

```shell
# Unit tests only (fast, Docker-free)
./gradlew test

# Component + contract (Docker required)
./gradlew componentTest
```

### Contract distribution

The consumer pact is read from the repo-owned `contracts/` folder via
`@PactFolder("../../../contracts")` — a **$0, zero-infra** default (committed pact,
no service). A Pact Broker is a separate, **cost-labelled** opt-in for multi-repo
setups only. See [`contracts/README.md`](../../../contracts/README.md) for the
distribution rule and broker cost details.

## Configuration

Configuration can be modified in `src/main/resources/application.yml`:

- `server.port` - Server port (default: 8081)