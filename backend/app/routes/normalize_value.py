from fastapi import APIRouter, HTTPException
from app.schemas import NormalizeValueRequest, NormalizeValueResponse
from app.services.gemini_service import normalize_value_gemini

router = APIRouter()

@router.post("/v1/normalize-value", response_model=NormalizeValueResponse)
def normalize_value(payload: NormalizeValueRequest):
    if not payload.transcript.strip():
        raise HTTPException(status_code=400, detail="Transcript cannot be empty")
    if not payload.input_type.strip():
        raise HTTPException(status_code=400, detail="input_type is required")

    return normalize_value_gemini(payload.locale, payload.input_type, payload.transcript)
