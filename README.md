# MiniChat Monorepo

Multi-service "ChatGPT-like" system.

## Milestone Status
Current repository is at **Milestone 3**:
- M1: monorepo + infra + service scaffolds
- M2: Spring auth/chats/messages + PostgreSQL migration
- M3: generation lifecycle + SSE streaming + cancel + Spring->FastAPI orchestration

## Structure

```text
apps/
  web/         # Next.js scaffold
  api/         # Spring Boot control plane
  inference/   # FastAPI inference plane
  worker/      # Go scaffold (M4 will consume events)
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

3. Start inference:

```bash
cd /Users/xuhaidong/Desktop/project/miniChat/apps/inference
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

4. Start API:

```bash
cd /Users/xuhaidong/Desktop/project/miniChat/apps/api
gradle bootRun
```

## Implemented API

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

Generation create body:

```json
{
  "userMessage": "hello",
  "model": "gpt-4o-mini",
  "systemPrompt": "You are helpful",
  "temperature": 0.7,
  "maxTokens": 512,
  "requestId": "optional-idempotency-key"
}
```

## Notes
- Generation state machine: `queued -> streaming -> succeeded|failed|canceled`
- SSE event protocol: `delta`, `usage`, `error`, `done`
- Redis enforces simple QPS + single in-flight generation lock per user
- RabbitMQ events are published for `usage_event` and `audit_event`
