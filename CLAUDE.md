# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with this repository.

## Project Overview

FixTestDriver is a specialized tool for testing FIX (Financial Information eXchange) protocol engines. It runs test scenarios defined in custom `.def` scripting files, supporting both Initiator and Acceptor sessions within the same engine. The project was originally a Java 6 Swing app and was fully modernized in 2026 to a cloud-ready web platform.

See also: [[README.md]], [[GEMINI.md]], [[TODO.md]] for project history, coding standards, and roadmap.

## Directory Structure

| Directory | Purpose |
|---|---|
| `/modern` | Modern Spring Boot 3.3 backend (Java 21, QuickFIX/J 2.3.1) |
| `/modern-ui` | React 18 frontend (Vite, TypeScript, Tailwind CSS) |
| `/trunk` | Legacy Java 6 Swing source (reference only — do not modify) |
| `/config` | Externalized FIX engine config (`FixEngine.cfg`), products dict, QuickFIX dictionaries |
| `/data` | Persistent H2 database files and test cases (`testcases/`) |
| `/logs` | FIX engine and application logs |

## Key Architecture

### Backend (`modern/`)
- **Entry point**: `FixTestDriverApplication.java` — Spring Boot with `@EnableAsync`
- **Core services**:
  - `FixEngineService` — wraps QuickFIX/J `Application`, manages Initiator/Acceptor lifecycle, broadcasts messages via WebSocket (`SimpMessagingTemplate`) to `/topic/messages` and `/topic/sessions`
  - `TestRunnerService` — parses `.def` scripts into `TestStep` instances and executes them sequentially
  - `MessageTransformationService` — transforms raw FIX message strings (pipe-delimited) into QuickFIX `Message` objects
  - `OrderManagerService` / `DictionaryService` — order tracking and dictionary browsing
- **Test step types**: `SendMessageStep`, `ExpectMessageStep`, `ConnectStep`, `WaitStep` (all implement `TestStep` interface)
- **Controllers**: `SessionController` (`/api/sessions`), `TestController` (`/api/tests`), `DictionaryController` (`/api/dictionary`)
- **Config**: `application.yml` — all externalized paths are env-var driven (see Docker section)

### Frontend (`modern-ui/`)
- **App**: Single-page app with tab-based navigation (Sessions | Tests | Logs)
- **WebSocket client**: Uses `@stomp/stompjs` + SockJS, subscribes to `/topic/messages` and `/topic/sessions` topics
- **API client**: Axios instance with `VITE_API_BASE_URL` (defaults to `http://localhost:8080/api`)
- **Components**: `SessionDashboard`, `TestRunner`, `MessageLog`, `Layout`, `FixMessageEditorModal`

### `.def` Script Language
Test scripts use a line-based DSL:
- `I...` — send a FIX message (pipe-delimited, e.g., `I8=DOM|9=47|35=D|...`)
- `E...` — expect a FIX message (with regex matching, 10s timeout)
- `WAIT <ms>` — wait N milliseconds
- `i<id> CONNECT <sessionId>` — connect to a session

## Development Commands

### Prerequisites
- JDK 21+, Node.js 18+, Maven 3.9+
- Docker (optional, for containerized deployment)

### Backend (from `/modern`)
```bash
mvn spring-boot:run          # Start backend on :8080
mvn test                     # Run all tests
mvn test -Dtest=LoopbackTest # Run a single test
```

### Frontend (from `/modern-ui`)
```bash
npm install                  # Install dependencies
npm run dev                  # Start dev server on :5173 (proxied to backend :8080)
npm run build                # TypeScript check + Vite production build
npm run lint                 # ESLint
```

### Docker Compose (from repo root)
```bash
docker compose up --build -d   # Build and start both services
docker compose logs -f         # Stream logs
```
- UI accessible at `http://localhost:3001`, API at `http://localhost:8080`
- Volume mounts: `./config:/app/config`, `./data:/app/data`, `./logs:/app/logs`

### Environment Variables (Backend)
| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:h2:file:./data/fixtestdriver;AUTO_SERVER=TRUE` | H2 DB path |
| `FIX_CONFIG_PATH` | `./config/FixEngine.cfg` | FIX engine config |
| `TESTCASES_ROOT` | `./data/testcases` | Test scripts directory |
| `PRODUCTS_DEF_PATH` | `./config/products.def` | Product dictionary |
| `QUICKFIX_DICT_PATH` | `./config/quickfix` | QuickFIX dict XMLs |

### Environment Variables (Frontend)
| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | REST API base URL |
| `VITE_WS_URL` | `http://localhost:8080/ws-fix` | WebSocket endpoint |

## Coding Standards (from GEMINI.md)
- **Hexagonal Architecture**: Keep core FIX logic decoupled from Spring Boot infra and Web UI
- **Service Layer**: All business operations in `@Service` classes
- **Async Execution**: Use `@Async` or Project Loom virtual threads for concurrent sessions
- **Event-Driven UI**: WebSocket (STOMP/SockJS) pushes message logs and session status updates
- **Java Features**: Records for DTOs, pattern matching `switch`, text blocks for templates
- **Testing**: JUnit 5, AssertJ, Mockito — every new feature needs corresponding tests

### React/TypeScript JSX Rules
- **No `{/* ...*/}` comments inside ternary branches in `return` statements** — TypeScript's JSX parser chokes on a comment as the first child of a parenthesised ternary branch (e.g., `{condition ? {/* comment */} <div/> : null}`). If you need a label, wrap the branch in a fragment `<>{/* comment */}<div/></>` or use a plain JavaScript `//` comment before the expression instead.

## Working with Legacy Code
The `/trunk` directory contains the original Java 6 Swing application. Use it only as a reference when porting features that haven't been migrated yet (e.g., `UserStep`, `DictionaryEditor`). Never modify files in `/trunk`.
