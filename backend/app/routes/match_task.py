from fastapi import APIRouter, HTTPException
from app.schemas import MatchTaskRequest, MatchTaskResponse
from app.services.gemini_service import match_task_gemini

router = APIRouter()

@router.post("/v1/match-task", response_model=MatchTaskResponse)
def match_task(payload: MatchTaskRequest):
    if not payload.transcript.strip():
        raise HTTPException(status_code=400, detail="Transcript cannot be empty")
    if not payload.candidates:
        raise HTTPException(status_code=400, detail="Candidates list cannot be empty")

    return match_task_gemini(payload.transcript, payload.candidates)
