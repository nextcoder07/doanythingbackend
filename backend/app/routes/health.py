from fastapi import APIRouter
from app.schemas import HealthResponse

router = APIRouter()

@router.get("/healthz", response_model=HealthResponse)
def health_check():
    return HealthResponse(status="ok", service="hakdaar-api")
