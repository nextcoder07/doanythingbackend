from fastapi import APIRouter, HTTPException
from app.schemas import ExtractFieldsRequest, ExtractFieldsResponse
from app.services.gemini_service import extract_fields_gemini

router = APIRouter()

@router.post("/v1/extract-fields", response_model=ExtractFieldsResponse)
def extract_fields(payload: ExtractFieldsRequest):
    if not payload.transcript.strip():
        raise HTTPException(status_code=400, detail="Transcript cannot be empty")
    if not payload.fields:
        raise HTTPException(status_code=400, detail="Fields schema cannot be empty")

    return extract_fields_gemini(payload.locale, payload.transcript, payload.fields)
