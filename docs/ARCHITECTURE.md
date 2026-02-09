# MiniChat Architecture

## 1. Overview
MiniChat is a multi-service "ChatGPT-like" system in a monorepo:
- Web: Next.js (TypeScript)
- API control plane: Spring Boot (Java 17)
- Inference data plane: FastAPI (Python)
- Async worker: Go
- Infra: PostgreSQL + Redis + RabbitMQ

All external clients talk only to Spring Boot. Spring orchestrates auth, chats/messages/generations, rate limits, persistence, and event publishing. FastAPI only handles model orchestration and streaming tokens. Go worker consumes queue events and writes usage/audit aggregates.

## 2. Service Boundaries

### 2.1 Spring Boot (Control Plane)
Responsibilities:
- Auth: register/login/refresh/me (JWT)
- Chat CRUD and message history
- Generation lifecycle: create, stream, cancel
- Redis-based controls: per-user inflight generation lock + basic rate limit
- Proxy internal streaming from FastAPI to browser SSE
- Persist generation state machine transitions
- Publish `usage_event` and `audit_event` to RabbitMQ

Non-responsibilities:
- No direct call to LLM provider APIs

### 2.2 FastAPI (Inference Plane)
Responsibilities:
- Provider abstraction and routing (initially OpenAI provider)
- Prompt/context assembly (`system + history`)
- Unified streaming event protocol output to Spring
- Cancellation endpoint by `generationId` (in-memory map in single-node mode)

Non-responsibilities:
- No user/auth management
- No writes to core business tables (`chats`, `messages`, `generations`)

### 2.3 Go Worker (Async Consumer)
Responsibilities:
- Consume `usage_event` and `audit_event`
- Write `usage_events`, update `daily_usage`
- Write `audit_logs`
- Structured logs and basic ack/nack handling

## 3. Data Storage

### 3.1 PostgreSQL (schema managed by Spring migrations)
Core tables:
- `users(id, email, password_hash, created_at)`
- `chats(id, user_id, title, created_at, updated_at)`
- `messages(id, chat_id, role, content, created_at)`
- `generations(id, chat_id, user_id, status, model, system_prompt, temperature, max_tokens, input_tokens, output_tokens, started_at, finished_at, error_code, error_message, request_id)`
- `usage_events(id, user_id, generation_id, input_tokens, output_tokens, created_at, model)`
- `daily_usage(id, user_id, day, input_tokens, output_tokens)` with unique `(user_id, day)`
- `audit_logs(id, user_id, action, metadata_json, created_at)`

### 3.2 Redis
- Rate limit buckets
- Inflight generation lock (`user:{userId}:inflight_generation`)

### 3.3 RabbitMQ
- `usage_event`
- `audit_event`

## 4. External API (Spring)
- Auth: `/api/auth/register`, `/api/auth/login`, `/api/auth/me`
- Chats: `/api/chats` CRUD
- Messages: `/api/chats/{id}/messages`
- Generations:
  - `POST /api/chats/{id}/generations` -> returns `generationId`
  - `GET /api/generations/{generationId}/stream` (SSE)
  - `POST /api/generations/{generationId}/cancel`

## 5. Internal API (Spring -> FastAPI)
- `POST /internal/generate` (streaming)
- `POST /internal/cancel`

## 6. Streaming Event Protocol
SSE JSON events from FastAPI, transparently forwarded by Spring to browser:
- `{ "type": "delta", "delta": "..." }`
- `{ "type": "usage", "inputTokens": 123, "outputTokens": 45 }`
- `{ "type": "error", "code": "...", "message": "..." }`
- `{ "type": "done" }`

## 7. Generation State Machine
Suggested states:
- `queued` -> `streaming` -> `succeeded`
- `queued|streaming` -> `canceled`
- `queued|streaming` -> `failed`

State transition ownership:
- Spring persists all transitions
- FastAPI emits stream events and termination reason

## 8. Milestones
- M1: Monorepo + infra + scaffolds (no business logic)
- M2: Auth + chat/message CRUD + DB migrations
- M3: Generation orchestration + SSE + cancel
- M4: RabbitMQ events + Go worker persistence + aggregation
- M5: rate limit/concurrency, hardening, docs and tests
