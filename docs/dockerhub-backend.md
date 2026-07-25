# FixTestDriver — Backend

Spring Boot backend for **FixTestDriver**, a specialized tool for testing FIX
(Financial Information eXchange) protocol engines. Wraps QuickFIX/J to manage
Initiator/Acceptor sessions, run `.def` test scripts, and stream FIX messages to
the UI over WebSocket.

- **Stack:** Java 21, Spring Boot 3.3, QuickFIX/J 2.3.1.
- **Architecture:** amd64 + arm64 (multi-arch manifest).
- **Ports:** `8080` (REST + WebSocket), `9888` (local loopback), FIX ports as configured.

## 📖 Documentation

Full documentation, capabilities, and usage live on the **frontend** image page:

➡️ **[lamiaconsultancy/fixtestdriver-frontend](https://hub.docker.com/r/lamiaconsultancy/fixtestdriver-frontend)**

## Quick start

Run alongside the frontend with Docker Compose (see the frontend page for the
full `docker-compose.yml`):

```bash
docker compose up -d
```

## Tags

- `latest` — most recent build (multi-arch: `linux/amd64`, `linux/arm64`).
