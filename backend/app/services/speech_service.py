import io
import wave
from fastapi import HTTPException
from app.config import settings

def validate_wav_audio(audio_bytes: bytes) -> None:
    """Validates that audio is valid 16kHz, mono, 16-bit PCM WAV within size/duration limits."""
    if len(audio_bytes) > settings.MAX_AUDIO_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Audio payload exceeds maximum allowed size ({settings.MAX_AUDIO_BYTES} bytes)"
        )
    
    if len(audio_bytes) < 44:
        raise HTTPException(status_code=400, detail="Invalid audio: file is too small to contain a WAV header")

    try:
        with wave.open(io.BytesIO(audio_bytes), "rb") as wav_file:
            n_channels = wav_file.getnchannels()
            sample_width = wav_file.getsampwidth()
            framerate = wav_file.getframerate()
            n_frames = wav_file.getnframes()
            duration_sec = n_frames / float(framerate) if framerate > 0 else 0

            if n_channels != 1:
                raise HTTPException(status_code=400, detail=f"Audio must be mono (found {n_channels} channels)")
            if sample_width != 2:
                raise HTTPException(status_code=400, detail=f"Audio must be 16-bit PCM (sample width 2 bytes, found {sample_width})")
            if framerate != 16000:
                raise HTTPException(status_code=400, detail=f"Audio must have 16000 Hz sample rate (found {framerate})")
            if duration_sec > settings.MAX_AUDIO_SECONDS:
                raise HTTPException(
                    status_code=400,
                    detail=f"Audio duration ({round(duration_sec, 1)}s) exceeds max allowed {settings.MAX_AUDIO_SECONDS}s"
                )
    except wave.Error as e:
        raise HTTPException(status_code=400, detail=f"Invalid WAV format: {str(e)}")

def transcribe_audio_v2(audio_bytes: bytes, language_code: str, request_id: str = "") -> str:
    """
    Calls Google Cloud Speech-to-Text V2 Recognize with:
    - model chirp_2
    - location asia-southeast1
    - language_code (hi-IN or en-IN)
    - synchronous recognition
    
    If Chirp 2 is not entitled for the GCP project or credentials are not configured,
    reports the exact blocker without silent fallback.
    """
    if language_code not in ["hi-IN", "en-IN"]:
        raise HTTPException(status_code=400, detail=f"Unsupported language code '{language_code}'. Must be 'hi-IN' or 'en-IN'.")

    validate_wav_audio(audio_bytes)

    # Check GCP project configuration
    if not settings.GCP_PROJECT_ID:
        raise HTTPException(
            status_code=503,
            detail="GCP_CONFIG_BLOCKER: GCP_PROJECT_ID is not configured in backend environment. "
                   "Google Cloud Speech-to-Text V2 requires a valid GCP Project ID."
        )

    try:
        from google.cloud import speech_v2
        from google.api_core.client_options import ClientOptions
        from google.api_core.exceptions import GoogleAPICallError, PermissionDenied, NotFound

        api_endpoint = f"{settings.STT_LOCATION}-speech.googleapis.com"
        client_options_args = {"api_endpoint": api_endpoint}
        if settings.STT_API_KEY:
            client_options_args["api_key"] = settings.STT_API_KEY

        client = speech_v2.SpeechClient(
            client_options=ClientOptions(**client_options_args)
        )

        recognizer_path = f"projects/{settings.GCP_PROJECT_ID}/locations/{settings.STT_LOCATION}/recognizers/_"

        config = speech_v2.RecognitionConfig(
            model=settings.STT_MODEL,
            language_codes=[language_code],
            features=speech_v2.RecognitionFeatures(
                enable_automatic_punctuation=True,
            ),
            auto_decoding_config=speech_v2.AutoDetectDecodingConfig(),
        )

        request = speech_v2.RecognizeRequest(
            recognizer=recognizer_path,
            config=config,
            content=audio_bytes,
        )

        response = client.recognize(request=request)

        transcripts = []
        for result in response.results:
            if result.alternatives:
                transcripts.append(result.alternatives[0].transcript)

        full_transcript = " ".join(transcripts).strip()
        if not full_transcript:
            raise HTTPException(
                status_code=422,
                detail="SPEECH_RECOGNITION_EMPTY: No audible speech could be recognized in the provided audio."
            )

        return full_transcript

    except PermissionDenied as pe:
        # Explicit Chirp 2 Private GA access check / IAM blocker
        raise HTTPException(
            status_code=503,
            detail=f"CHIRP2_ACCESS_DENIED: Speech-to-Text V2 model '{settings.STT_MODEL}' in '{settings.STT_LOCATION}' "
                   f"denied access for project '{settings.GCP_PROJECT_ID}'. Chirp 2 is Private GA in asia-southeast1 "
                   f"and requires project entitlement or IAM role 'roles/speech.client'. Details: {pe.message}"
        )
    except NotFound as ne:
        raise HTTPException(
            status_code=503,
            detail=f"STT_ENDPOINT_NOT_FOUND: Recognizer or location '{settings.STT_LOCATION}' not found: {ne.message}"
        )
    except GoogleAPICallError as ge:
        raise HTTPException(
            status_code=502,
            detail=f"GCP_STT_ERROR: Speech-to-Text API returned an error: {ge.message}"
        )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"STT_UNEXPECTED_ERROR: Speech service initialization failed: {str(e)}"
        )
