import asyncio
import json
from typing import Any

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

router = APIRouter(prefix="/internal", tags=["internal"])


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


@router.post("/generate")
async def generate(payload: GenerateRequest) -> StreamingResponse:
    async def event_stream() -> Any:
        # Placeholder streaming protocol for milestone-1 scaffolding.
        del payload
        yield f"data: {json.dumps({'type': 'delta', 'delta': 'scaffold response'})}\n\n"
        await asyncio.sleep(0.05)
        yield f"data: {json.dumps({'type': 'usage', 'inputTokens': 1, 'outputTokens': 2})}\n\n"
        yield f"data: {json.dumps({'type': 'done'})}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.post("/cancel")
def cancel(_: CancelRequest) -> dict[str, str]:
    return {"status": "accepted"}
