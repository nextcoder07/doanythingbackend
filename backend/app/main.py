import os
from pathlib import Path
from fastapi import FastAPI, Response
from fastapi.responses import HTMLResponse
from app.services.privacy_safe_logging import PrivacySafeLoggingMiddleware
from app.routes import health, transcribe, match_task, extract_fields, normalize_value

app = FastAPI(
    title="Hakdaar API",
    description="Stateless prototype backend for Hakdaar voice form-filling assistant",
    version="1.0.0",
    docs_url=None,  # Disable swagger docs in production / prototype to reduce exposure
    redoc_url=None
)

# Attach privacy-safe logging middleware (never logs request bodies, transcripts, or personal data)
app.add_middleware(PrivacySafeLoggingMiddleware)

# API routes
app.include_router(health.router)
app.include_router(transcribe.router)
app.include_router(match_task.router)
app.include_router(extract_fields.router)
app.include_router(normalize_value.router)

PORTAL_DIR = Path(__file__).parent / "demo_portal"

@app.get("/demo/birth-certificate", response_class=HTMLResponse)
def get_birth_certificate_demo():
    file_path = PORTAL_DIR / "birth_certificate.html"
    return HTMLResponse(content=file_path.read_text(encoding="utf-8"))

@app.get("/demo/widow-pension", response_class=HTMLResponse)
def get_widow_pension_demo():
    file_path = PORTAL_DIR / "widow_pension.html"
    return HTMLResponse(content=file_path.read_text(encoding="utf-8"))
