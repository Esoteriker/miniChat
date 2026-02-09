# MiniChat Monorepo

Multi-service "ChatGPT-like" system.

## Milestone Status
Current repository is at **Milestone 2**:
- Milestone 1: monorepo + infra + multi-service scaffolds
- Milestone 2: Spring Boot implemented with
  - JWT auth: `register/login/refresh/me`
  - chat CRUD: `create/list/rename/delete`
  - messages API: `list(cursor,limit)/create`
  - Flyway migration with core tables (`users/chats/messages/generations/usage_events/daily_usage/audit_logs`)

## Structure

```text
apps/
  web/         # Next.js scaffold
  api/         # Spring Boot (implemented in M2)
  inference/   # FastAPI scaffold
  worker/      # Go scaffold
infra/
  docker-compose.yml
shared/
  contracts/
docs/
  ARCHITECTURE.md
PLANS.md
AGENTS.md
.env.example
```

## Ports
- Web: `3000`
- API: `8080`
- Inference: `8000`
- PostgreSQL: `5432`
- Redis: `6379`
- RabbitMQ: `5672`
- RabbitMQ management: `15672`

## Quick Start

1. Prepare env:

```bash
cp /Users/xuhaidong/Desktop/project/miniChat/.env.example /Users/xuhaidong/Desktop/project/miniChat/.env
```

2. Start infra:

```bash
docker compose -f /Users/xuhaidong/Desktop/project/miniChat/infra/docker-compose.yml --env-file /Users/xuhaidong/Desktop/project/miniChat/.env up -d
```

3. Start API:

```bash
cd /Users/xuhaidong/Desktop/project/miniChat/apps/api
SPRING_PROFILES_ACTIVE=local gradle bootRun
```

4. Health check:

```bash
curl http://localhost:8080/healthz
curl http://localhost:8080/actuator/health
```

## Implemented API (Milestone 2)

### Auth
- `POST /api/auth/register` `{ "email": "...", "password": "..." }`
- `POST /api/auth/login` `{ "email": "...", "password": "..." }`
- `POST /api/auth/refresh` (Bearer token)
- `GET /api/auth/me` (Bearer token)

### Chats
- `POST /api/chats` `{ "title": "optional" }`
- `GET /api/chats`
- `PATCH /api/chats/{id}` `{ "title": "new title" }`
- `DELETE /api/chats/{id}`

### Messages
- `GET /api/chats/{id}/messages?cursor=&limit=`
- `POST /api/chats/{id}/messages` `{ "content": "..." }`

## Notes
- `cursor` is message UUID for keyset pagination.
- `limit` default `20`, max `100`.
- Milestone 3 will add generation state machine, SSE streaming, cancel, and inference integration.
