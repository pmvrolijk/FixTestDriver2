# FixTestDriver Modernization - Upgrade Path (TODO)

## Phase 1: Foundation
- [x] Initialize Maven project with Spring Boot 3.3.
- [x] Set up Java 21 environment.
- [x] Configure `pom.xml` with dependencies:
    - `spring-boot-starter-web`
    - `spring-boot-starter-data-jpa`
    - `quickfixj-core` (2.3.1)
    - `quickfixj-messages-all` (2.3.1)
    - `h2` / `postgresql`
    - `lombok`
- [ ] Set up Logback with SLF4J.

## Phase 2: Core Logic Migration
- [x] Port `OrderManager` (Convert HSQLDB logic to JPA).
- [x] Port `Dictionary` (Modern XML parsing and validation).
- [x] Port `TestMessage` (Refactor macro substitution logic using modern Regex and `java.time`).
- [x] Implement `FixEngineService` (Wrapper around QuickFIX/J `Application`).
- [x] Add support for FIX Acceptor sessions in `FixEngineService`.

## Phase 3: Test Execution Engine
- [x] Implement `TestStep` interface and modern implementations:
    - [x] `ConnectStep`
    - [x] `SendMessageStep`
    - [x] `ExpectMessageStep` (with regex and timeout)
- [x] Create `TestRunner` service to execute `.def` scripts.
- [ ] Implement persistence for `TestResult` and `MessageLog`.

## Phase 4: Web API
- [x] Create REST controllers for:
    - [x] Session management (start/stop/status).
    - [x] Test suite management (upload/list/run).
    - [x] Dictionary browsing.
- [x] Implement WebSocket configuration for real-time streaming.

## Phase 5: Frontend (React)
- [x] Initialize React project with Vite.
- [x] Implement Session Dashboard.
- [x] Implement Test Runner UI.
- [x] Implement Message Log Viewer (with FIX tag highlighting).

## Phase 6: Validation
- [x] Run existing `.def` test cases to verify parity (Loopback Test Passed).
- [ ] Perform load testing for multiple concurrent sessions.

## Phase 7: Cloud Readiness & Configuration
- [x] Add `spring-boot-starter-actuator` dependency.
- [x] Externalize backend properties in `application.yml`.
- [x] Configure health and readiness probes in `application.yml`.
- [x] Support file-based H2 persistence in `/data`.
- [x] Refactor services to use externalized paths for configs and dictionaries.
- [x] Set up frontend `.env` and `.env.local`.
- [x] Document Docker/K8S volume mount requirements.

## Phase 8: Containerization
- [x] Create multi-stage Dockerfile for backend.
- [x] Create multi-stage Dockerfile for frontend.
- [x] Create `docker-compose.yml` for service orchestration.
- [x] Verify deployment via `docker compose up`.

