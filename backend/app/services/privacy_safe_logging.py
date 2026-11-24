import logging
import time
import uuid
from typing import Callable
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger("hakdaar_api")
logger.setLevel(logging.INFO)

handler = logging.StreamHandler()
handler.setFormatter(logging.Formatter("[%(asctime)s] [%(levelname)s] [request_id=%(request_id)s] %(message)s"))
logger.addHandler(handler)

class RequestIdFilter(logging.Filter):
    def __init__(self, request_id: str):
        super().__init__()
        self.request_id = request_id

    def filter(self, record):
        record.request_id = self.request_id
        return True

class PrivacySafeLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        request_id = str(uuid.uuid4())
        request.state.request_id = request_id

        # Bind request_id filter
        req_filter = RequestIdFilter(request_id)
        logger.addFilter(req_filter)

        start_time = time.time()
        endpoint = request.url.path
        method = request.method
        content_length = request.headers.get("content-length", "0")

        # Log ingress (endpoint, method, byte count ONLY - NO body, NO personal data)
        logger.info(
            f"METHOD={method} ENDPOINT={endpoint} BYTES_IN={content_length} STATUS=RECEIVED",
            extra={"request_id": request_id}
        )

        try:
            response = await call_next(request)
            duration_ms = round((time.time() - start_time) * 1000, 2)
            status_code = response.status_code

            # Log egress (status code, duration ONLY - NO body, NO personal data)
            logger.info(
                f"METHOD={method} ENDPOINT={endpoint} STATUS_CODE={status_code} DURATION_MS={duration_ms} STATUS=COMPLETED",
                extra={"request_id": request_id}
            )
            response.headers["X-Request-ID"] = request_id
            return response
        except Exception as exc:
            duration_ms = round((time.time() - start_time) * 1000, 2)
            logger.error(
                f"METHOD={method} ENDPOINT={endpoint} ERROR_TYPE={type(exc).__name__} DURATION_MS={duration_ms} STATUS=FAILED",
                extra={"request_id": request_id}
            )
            raise
        finally:
            logger.removeFilter(req_filter)

def log_safe_event(request_id: str, event_name: str, status: str, details: str = ""):
    """Logs safe metadata only. Never log user speech, transcript, or form values."""
    logger.info(
        f"EVENT={event_name} STATUS={status} DETAILS={details}",
        extra={"request_id": request_id}
    )
