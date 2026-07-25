# FixTestDriver — Frontend (UI)

Web UI for **FixTestDriver**, a specialized tool for testing FIX (Financial
Information eXchange) protocol engines. This image serves the React single-page
app via Nginx and proxies API/WebSocket traffic to the backend.

> This is the **frontend** image. It requires the companion backend image:
> [`lamiaconsultancy/fixtestdriver-backend`](https://hub.docker.com/r/lamiaconsultancy/fixtestdriver-backend).

## Overview

FixTestDriver runs test scenarios defined in a custom `.def` scripting language,
supporting both Initiator and Acceptor FIX sessions within the same engine. The
UI is a tab-based single-page app (Sessions | Tests | Logs) that talks to the
backend over REST and receives live message/session updates over STOMP/SockJS
WebSockets.

- **Stack:** React 18, Vite, TypeScript, Tailwind CSS, served by Nginx (Alpine).
- **Architecture:** amd64 + arm64 (multi-arch manifest).
- **Ports:** `80` (HTTP).

## Capabilities

- **Session dashboard** — start/stop Initiator and Acceptor sessions, view
  status, and sync sequence numbers.
- **Test runner** — execute `.def` test scripts and watch steps run in real time.
- **Live message log** — FIX messages and session status pushed over WebSocket.
- **FIX message editor** — compose and send pipe-delimited FIX messages.
- **Built-in reverse proxy** — Nginx forwards `/api` and `/ws-fix` to the backend,
  so the browser only needs to reach the frontend.

## Usage

### Docker Compose (recommended)

The frontend proxies to a service named `backend`, so run both together:

```yaml
services:
  backend:
    image: lamiaconsultancy/fixtestdriver-backend:latest
    container_name: fixtestdriver-backend
    ports:
      - "8080:8080"
      - "9888:9888"
    volumes:
      - ./config:/app/config
      - ./data:/app/data
      - ./logs:/app/logs
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health/liveness"]
      interval: 30s
      timeout: 10s
      retries: 3

  frontend:
    image: lamiaconsultancy/fixtestdriver-frontend:latest
    container_name: fixtestdriver-frontend
    ports:
      - "3001:80"
    depends_on:
      backend:
        condition: service_healthy
    restart: unless-stopped
```

```bash
docker compose up -d
```

Then open **http://localhost:3001**.

### Standalone

```bash
docker run -d -p 3001:80 lamiaconsultancy/fixtestdriver-frontend:latest
```

Standalone only serves the static UI; `/api` and `/ws-fix` proxying expects a
reachable `backend` host, so Compose (or a shared network) is recommended.

## Configuration

The app is built with **relative** API paths so it works behind the bundled Nginx
proxy on any host:

| Build arg | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | REST API base path (proxied to backend) |
| `VITE_WS_URL` | `/ws-fix` | WebSocket endpoint (proxied to backend) |

These are baked in at build time. The published image uses the defaults above;
override them only if you rebuild for a non-proxied deployment.

## Tags

- `latest` — most recent build (multi-arch: `linux/amd64`, `linux/arm64`).

## Dockerfile

```dockerfile
# Stage 1: Build
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# These can be overridden by build args if needed
ARG VITE_API_BASE_URL=/api
ARG VITE_WS_URL=/ws-fix
RUN npm run build

# Stage 2: Runtime (Nginx)
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html

# Custom Nginx config to handle SPA routing and proxying
RUN echo 'server { \
    listen 80; \
    location / { \
        root /usr/share/nginx/html; \
        index index.html index.htm; \
        try_files $uri $uri/ /index.html; \
    } \
    location /api { \
        proxy_pass http://backend:8080; \
        proxy_set_header Host $host; \
        proxy_set_header X-Real-IP $remote_addr; \
    } \
    location /ws-fix { \
        proxy_pass http://backend:8080; \
        proxy_http_version 1.1; \
        proxy_set_header Upgrade $http_upgrade; \
        proxy_set_header Connection "Upgrade"; \
        proxy_set_header Host $host; \
    } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```
