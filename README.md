# MiniChat Monorepo

Multi-service "ChatGPT-like" system.

## Milestone Status
Current repository is at **Milestone 5**:
- M1: monorepo + infra + scaffolds
- M2: Spring auth/chats/messages + Flyway schema
- M3: generation lifecycle + SSE streaming + cancel
- M4: Go worker consumes `usage_event` / `audit_event` and writes aggregates
- M5: web app connected end-to-end + after-commit event publishing + CORS

## Services
- `apps/web`: Next.js app (`/login`, `/chat`) with JWT auth, chats, messages, streaming generation, stop
- `apps/api`: Spring Boot control plane
- `apps/inference`: FastAPI inference plane
- `apps/worker`: Go queue consumer
- Infra: PostgreSQL + Redis + RabbitMQ

## Quick Start

```bash
cp /Users/xuhaidong/Desktop/project/miniChat/.env.example /Users/xuhaidong/Desktop/project/miniChat/.env

# Use a non-conflicting host port for PostgreSQL if 5432 is already used.
POSTGRES_PORT=55432 docker compose -f /Users/xuhaidong/Desktop/project/miniChat/infra/docker-compose.yml --env-file /Users/xuhaidong/Desktop/project/miniChat/.env up -d --build
```

Start web:

```bash
cd /Users/xuhaidong/Desktop/project/miniChat/apps/web
npm install
npm run dev
```

Open:
- Web: `http://localhost:3000`
- API: `http://localhost:8080`
- Inference: `http://localhost:8000`
- RabbitMQ UI: `http://localhost:15672`

## API Surface

### Auth
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`

### Chats
- `POST /api/chats`
- `GET /api/chats`
- `PATCH /api/chats/{id}`
- `DELETE /api/chats/{id}`

### Messages
- `GET /api/chats/{id}/messages?cursor=&limit=`
- `POST /api/chats/{id}/messages`

### Generations
- `POST /api/chats/{id}/generations`
- `GET /api/generations/{id}/stream` (SSE)
- `POST /api/generations/{id}/cancel`

## Notes
- Generation state machine: `queued -> streaming -> succeeded|failed|canceled`
- SSE event protocol: `delta`, `usage`, `error`, `done`
- RabbitMQ events are now published after DB transaction commit to avoid FK race in worker
- CORS is enabled for `WEB_ORIGIN` (default `http://localhost:3000`)

## Useful Verification

```bash
# API compile
docker run --rm -v /Users/xuhaidong/Desktop/project/miniChat/apps/api:/workspace -w /workspace gradle:8.10.2-jdk17 gradle compileJava --no-daemon

# Inference syntax
python3 -m py_compile /Users/xuhaidong/Desktop/project/miniChat/apps/inference/app/main.py /Users/xuhaidong/Desktop/project/miniChat/apps/inference/app/api/internal.py

# Worker compile
docker run --rm -v /Users/xuhaidong/Desktop/project/miniChat/apps/worker:/workspace -w /workspace golang:1.22-alpine go build ./...

# Web build
cd /Users/xuhaidong/Desktop/project/miniChat/apps/web && npm run build
```
