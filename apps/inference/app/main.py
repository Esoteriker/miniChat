from fastapi import FastAPI

from app.api.internal import router as internal_router

app = FastAPI(title="minichat-inference", version="0.1.0")
app.include_router(internal_router)


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok", "service": "inference"}
