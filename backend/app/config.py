import os
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

ENV_PATH = Path(__file__).resolve().parent.parent / ".env"

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(str(ENV_PATH), ".env"),
        extra="ignore"
    )

    GCP_PROJECT_ID: str = ""
    STT_PROVIDER: str = "google"
    STT_LOCATION: str = "asia-southeast1"
    STT_MODEL: str = "chirp_2"
    STT_API_KEY: str = ""
    OPENAI_API_KEY: str = ""
    GOOGLE_APPLICATION_CREDENTIALS: str = ""
    GEMINI_MODEL_ID: str = "gemini-3.8-flash"
    GEMINI_API_KEY: str = ""
    MAX_AUDIO_SECONDS: int = 55
    # 55 seconds * 16000 samples/sec * 2 bytes/sample (16-bit) + 44 bytes WAV header = 1,760,044 bytes
    MAX_AUDIO_BYTES: int = 1800000
    LOG_REQUEST_BODIES: bool = False

settings = Settings()

# If GOOGLE_APPLICATION_CREDENTIALS is provided in .env, export to os.environ for google-auth
if settings.GOOGLE_APPLICATION_CREDENTIALS:
    cred_path = Path(settings.GOOGLE_APPLICATION_CREDENTIALS)
    if not cred_path.is_absolute():
        cred_path = (Path(__file__).resolve().parent.parent / cred_path).resolve()
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = str(cred_path)


