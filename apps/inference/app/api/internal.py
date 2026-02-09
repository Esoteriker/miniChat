import asyncio
import json
from collections.abc import AsyncIterator
from typing import Any

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

router = APIRouter(prefix="/internal", tags=["internal"])

_cancel_registry: dict[str, asyncio.Event] = {}
_registry_lock = asyncio.Lock()


class Message(BaseModel):
    role: str
    content: str


class GenerateRequest(BaseModel):
    generation_id: str
    model: str = "gpt-4o-mini"
    system_prompt: str | None = None
    temperature: float = 0.7
    max_tokens: int = 512
    messages: list[Message]


class CancelRequest(BaseModel):
    generation_id: str


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


def _parse_messages(raw_messages: Any) -> list[Message]:
    if not isinstance(raw_messages, list):
        return []
    messages: list[Message] = []
    for item in raw_messages:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role", "")).strip()
        content = str(item.get("content", ""))
        if role and content:
            messages.append(Message(role=role, content=content))
    return messages


def _parse_generate_request(payload: dict[str, Any]) -> GenerateRequest:
    generation_id = str(payload.get("generationId") or payload.get("generation_id") or "").strip()
    model = str(payload.get("model") or "gpt-4o-mini").strip() or "gpt-4o-mini"
    system_prompt = payload.get("systemPrompt")
    if system_prompt is None:
        system_prompt = payload.get("system_prompt")
    if system_prompt is not None:
        system_prompt = str(system_prompt)

    temperature_raw = payload.get("temperature", 0.7)
    try:
        temperature = float(temperature_raw)
    except (TypeError, ValueError):
        temperature = 0.7

    max_tokens_raw = payload.get("maxTokens")
    if max_tokens_raw is None:
        max_tokens_raw = payload.get("max_tokens", 512)
    try:
        max_tokens = int(max_tokens_raw)
    except (TypeError, ValueError):
        max_tokens = 512

    messages = _parse_messages(payload.get("messages", []))

    if not generation_id:
        raise ValueError("generationId is required")

    return GenerateRequest(
        generation_id=generation_id,
        model=model,
        system_prompt=system_prompt,
        temperature=temperature,
        max_tokens=max_tokens,
        messages=messages,
    )


def _parse_cancel_request(payload: dict[str, Any]) -> CancelRequest:
    generation_id = str(payload.get("generationId") or payload.get("generation_id") or "").strip()
    if not generation_id:
        raise ValueError("generationId is required")
    return CancelRequest(generation_id=generation_id)


@router.post("/generate")
async def generate(payload: dict[str, Any]) -> StreamingResponse:
    req = _parse_generate_request(payload)
    cancel_event = await _register(req.generation_id)

    async def event_stream() -> AsyncIterator[str]:
        try:
            answer = _build_answer(req.messages)
            produced = ""

            for chunk in _chunks(answer):
                if cancel_event.is_set():
                    yield f"data: {json.dumps({'type': 'error', 'code': 'canceled', 'message': 'Generation canceled'})}\n\n"
                    yield f"data: {json.dumps({'type': 'done'})}\n\n"
                    return

                produced += chunk
                yield f"data: {json.dumps({'type': 'delta', 'delta': chunk})}\n\n"
                await asyncio.sleep(0.04)

            input_text = "\n".join([msg.content for msg in req.messages])
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
            await _unregister(req.generation_id)

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.post("/cancel")
async def cancel(payload: dict[str, Any]) -> dict[str, Any]:
    req = _parse_cancel_request(payload)
    async with _registry_lock:
        event = _cancel_registry.get(req.generation_id)
        if event is None:
            return {"status": "accepted", "found": False}
        event.set()
        return {"status": "accepted", "found": True}
