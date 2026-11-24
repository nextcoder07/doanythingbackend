from fastapi import APIRouter, Request, Header, HTTPException
from app.schemas import TranscribeResponse
from app.services.speech_service import transcribe_audio_v2

router = APIRouter()

@router.post("/v1/transcribe", response_model=TranscribeResponse)
async def transcribe(
    request: Request,
    content_type: str = Header(..., alias="Content-Type"),
    x_audio_language: str = Header(..., alias="X-Audio-Language")
):
    if not content_type.startswith("audio/wav"):
        raise HTTPException(status_code=415, detail="Unsupported Media Type: Content-Type must be audio/wav")
    
    if x_audio_language not in ["hi-IN", "en-IN"]:
        raise HTTPException(status_code=400, detail="X-Audio-Language must be 'hi-IN' or 'en-IN'")

    audio_bytes = await request.body()
    request_id = getattr(request.state, "request_id", "")

    transcript = transcribe_audio_v2(audio_bytes, x_audio_language, request_id)
    return TranscribeResponse(
        transcript=transcript,
        language_code=x_audio_language,
        status="ok"
    )
