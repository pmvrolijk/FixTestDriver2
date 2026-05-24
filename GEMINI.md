# Project Instructions - FixTestDriver Modernization

This document defines the architectural patterns, coding standards, and workflows for the modernized FixTestDriver project.

## 1. Architectural Patterns
- **Hexagonal Architecture**: Keep the core FIX business logic and test execution engine decoupled from the Spring Boot infrastructure and the Web UI.
- **Service Layer**: All business operations must be encapsulated in `@Service` classes.
- **Asynchronous Execution**: Use Spring's `@Async` or Project Loom (Virtual Threads) for handling concurrent FIX sessions and test steps.
- **Event-Driven UI**: Use WebSockets (STOMP/SockJS) to push FIX message logs and session status updates to the frontend.

## 2. Coding Standards
- **Java Version**: Java 21+.
- **Language Features**: Use modern features like Records for DTOs, Pattern Matching for `switch`, and Text Blocks for FIX dictionary XML/Templates.
- **Naming Conventions**: Standard Java naming conventions. Use descriptive names for FIX tags (e.g., `clOrdID` instead of `tag11`).
- **Immutability**: Prefer immutable objects and collections where possible.
- **Testing**: JUnit 5, AssertJ, and Mockito. Every new feature must have corresponding unit and integration tests.

## 3. Technical Requirements
- **FIX Protocol**: Native integration with QuickFIX/J 2.3.1. Ensure support for custom dictionaries.
- **Persistence**: Spring Data JPA with H2/PostgreSQL. Use Liquibase or Flyway for database migrations.
- **Security**: (Optional) Spring Security for access control if deployed as a shared tool.
- **API**: RESTful API for management, WebSocket for real-time data.

## 4. Documentation
- Keep Javadoc updated for public APIs.
- Document custom test definition macros in a dedicated `TESTING.md`.
