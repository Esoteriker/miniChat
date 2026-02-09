# MiniChat Monorepo

Multi-service "ChatGPT-like" system scaffold.

## Tech Stack
- `apps/web`: Next.js (TypeScript)
- `apps/api`: Spring Boot (Java 17)
- `apps/inference`: FastAPI (Python)
- `apps/worker`: Go worker
- Infra: PostgreSQL + Redis + RabbitMQ (`infra/docker-compose.yml`)

## Milestone Status
Current repository is at **Milestone 1**:
- monorepo directory initialized
- infra compose initialized (postgres/redis/rabbitmq)
- service scaffolds created
- business logic not implemented yet

## Project Structure

```text
apps/
  web/
  api/
  inference/
  worker/
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

## Quick Start (Infra Only)

1. Copy environment file:

```bash
cp /Users/xuhaidong/Desktop/project/miniChat/.env.example /Users/xuhaidong/Desktop/project/miniChat/.env
```

2. Start infrastructure:

```bash
docker compose -f /Users/xuhaidong/Desktop/project/miniChat/infra/docker-compose.yml --env-file /Users/xuhaidong/Desktop/project/miniChat/.env up -d
```

3. Check status:

```bash
docker compose -f /Users/xuhaidong/Desktop/project/miniChat/infra/docker-compose.yml ps
```

4. Stop and cleanup:

```bash
docker compose -f /Users/xuhaidong/Desktop/project/miniChat/infra/docker-compose.yml down -v
```

## Default Ports
- Web: `3000`
- API: `8080`
- Inference: `8000`
- PostgreSQL: `5432`
- Redis: `6379`
- RabbitMQ: `5672`
- RabbitMQ Management: `15672`

## Service Scaffold Run Commands (Optional)

- API:
  - `cd /Users/xuhaidong/Desktop/project/miniChat/apps/api && gradle bootRun`
- Inference:
  - `cd /Users/xuhaidong/Desktop/project/miniChat/apps/inference && uvicorn app.main:app --reload --port 8000`
- Worker:
  - `cd /Users/xuhaidong/Desktop/project/miniChat/apps/worker && go run ./cmd/worker`
- Web:
  - `cd /Users/xuhaidong/Desktop/project/miniChat/apps/web && npm install && npm run dev`
