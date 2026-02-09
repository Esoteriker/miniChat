from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.requests import Request
from fastapi.responses import JSONResponse
import logging

from app.api.internal import router as internal_router

app = FastAPI(title="minichat-inference", version="0.1.0")
app.include_router(internal_router)
logger = logging.getLogger("uvicorn.error")


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    body = await request.body()
    logger.error("request validation failed path=%s errors=%s body=%s", request.url.path, exc.errors(), body.decode("utf-8", "ignore"))
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok", "service": "inference"}
