import asyncio
import json
from collections.abc import AsyncIterator
from typing import Any

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

router = APIRouter(prefix="/internal", tags=["internal"])

_cancel_registry: dict[str, asyncio.Event] = {}
_registry_lock = asyncio.Lock()


class Message(BaseModel):
    role: str
    content: str


class GenerateRequest(BaseModel):
    generationId: str
    model: str = Field(default="gpt-4o-mini")
    systemPrompt: str | None = None
    temperature: float = 0.7
    maxTokens: int = 512
    messages: list[Message]


class CancelRequest(BaseModel):
    generationId: str


def _estimate_tokens(text: str) -> int:
    # Coarse token estimate for local milestone use.
    return max(1, len(text) // 4)


def _build_answer(messages: list[Message]) -> str:
    last_user = ""
    for msg in reversed(messages):
        if msg.role == "user":
            last_user = msg.content
            break

    if not last_user:
        return "I did not receive a user message."
    return f"Echo: {last_user}"


def _chunks(text: str, size: int = 12) -> list[str]:
    return [text[i : i + size] for i in range(0, len(text), size)]


async def _register(generation_id: str) -> asyncio.Event:
    async with _registry_lock:
        event = asyncio.Event()
        _cancel_registry[generation_id] = event
        return event


async def _unregister(generation_id: str) -> None:
    async with _registry_lock:
        _cancel_registry.pop(generation_id, None)


@router.post("/generate")
async def generate(payload: GenerateRequest) -> StreamingResponse:
    cancel_event = await _register(payload.generationId)

    async def event_stream() -> AsyncIterator[str]:
        try:
            answer = _build_answer(payload.messages)
            produced = ""

            for chunk in _chunks(answer):
                if cancel_event.is_set():
                    yield f"data: {json.dumps({'type': 'error', 'code': 'canceled', 'message': 'Generation canceled'})}\n\n"
                    yield f"data: {json.dumps({'type': 'done'})}\n\n"
                    return

                produced += chunk
                yield f"data: {json.dumps({'type': 'delta', 'delta': chunk})}\n\n"
                await asyncio.sleep(0.04)

            input_text = "\n".join([msg.content for msg in payload.messages])
            usage = {
                "type": "usage",
                "inputTokens": _estimate_tokens(input_text),
                "outputTokens": _estimate_tokens(produced),
            }
            yield f"data: {json.dumps(usage)}\n\n"
            yield f"data: {json.dumps({'type': 'done'})}\n\n"
        except Exception as ex:
            yield f"data: {json.dumps({'type': 'error', 'code': 'inference_error', 'message': str(ex)})}\n\n"
            yield f"data: {json.dumps({'type': 'done'})}\n\n"
        finally:
            await _unregister(payload.generationId)

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.post("/cancel")
async def cancel(payload: CancelRequest) -> dict[str, Any]:
    async with _registry_lock:
        event = _cancel_registry.get(payload.generationId)
        if event is None:
            return {"status": "accepted", "found": False}
        event.set()
        return {"status": "accepted", "found": True}
