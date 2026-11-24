import io
import wave
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.services.speech_service import validate_wav_audio
from app.services.gemini_service import match_task_gemini, extract_fields_gemini, normalize_value_gemini
from app.schemas import TaskCandidate, FieldDefinition

client = TestClient(app)

def create_dummy_wav(duration_sec: float = 1.0, framerate: int = 16000, n_channels: int = 1, sample_width: int = 2) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(n_channels)
        wav.setsampwidth(sample_width)
        wav.setframerate(framerate)
        num_frames = int(duration_sec * framerate)
        wav.writeframes(b"\x00" * (num_frames * n_channels * sample_width))
    return buffer.getvalue()

# Test Health
def test_healthz():
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "hakdaar-api"}

# Test Demo Portal
def test_demo_portal_pages():
    r1 = client.get("/demo/birth-certificate")
    assert r1.status_code == 200
    assert "Birth Certificate — Demo Form" in r1.text
    assert "child_name" in r1.text
    assert "date_of_birth" in r1.text
    assert "Demo complete — this was a local simulation" in r1.text

    r2 = client.get("/demo/widow-pension")
    assert r2.status_code == 200
    assert "Widow Pension — Demo Form" in r2.text
    assert "applicant_name" in r2.text
    assert "date_of_death" in r2.text
    assert "Demo complete — this was a local simulation" in r2.text

# Test Audio Validation & Transcribe Input Errors
def test_transcribe_audio_validation():
    # Unsupported language
    wav_bytes = create_dummy_wav(1.0)
    res = client.post(
        "/v1/transcribe",
        content=wav_bytes,
        headers={"Content-Type": "audio/wav", "X-Audio-Language": "fr-FR"}
    )
    assert res.status_code == 400
    assert "X-Audio-Language must be 'hi-IN' or 'en-IN'" in res.json()["detail"]

    # Unsupported Media Type
    res2 = client.post(
        "/v1/transcribe",
        content=b"not a wav",
        headers={"Content-Type": "text/plain", "X-Audio-Language": "hi-IN"}
    )
    assert res2.status_code == 415

    # Invalid WAV header
    res3 = client.post(
        "/v1/transcribe",
        content=b"RIFFshort",
        headers={"Content-Type": "audio/wav", "X-Audio-Language": "hi-IN"}
    )
    assert res3.status_code == 400

    # Stereo WAV rejection
    stereo_bytes = create_dummy_wav(1.0, n_channels=2)
    res4 = client.post(
        "/v1/transcribe",
        content=stereo_bytes,
        headers={"Content-Type": "audio/wav", "X-Audio-Language": "hi-IN"}
    )
    assert res4.status_code == 400
    assert "Audio must be mono" in res4.json()["detail"]

    # Overlong audio rejection (> 55 seconds)
    long_bytes = create_dummy_wav(56.0)
    res5 = client.post(
        "/v1/transcribe",
        content=long_bytes,
        headers={"Content-Type": "audio/wav", "X-Audio-Language": "hi-IN"}
    )
    assert res5.status_code == 400
    assert "exceeds max allowed 55s" in res5.json()["detail"]

# Test Task Matching
def test_task_matching():
    candidates = [
        TaskCandidate(id="birth_certificate_demo", title="Birth Certificate Application (Demo)", description="Sample birth certificate"),
        TaskCandidate(id="widow_pension_demo", title="Widow Pension Application (Demo)", description="Sample widow pension")
    ]

    # Birth Certificate match (Hindi/English aliases)
    m1 = match_task_gemini("Meri beti ka birth certificate banwana hai", candidates)
    assert m1.task_id == "birth_certificate_demo"
    assert m1.status == "matched"

    # Widow Pension match
    m2 = match_task_gemini("Vidhwa pension ke liye aavedan karna hai", candidates)
    assert m2.task_id == "widow_pension_demo"
    assert m2.status == "matched"

    # Ambiguous request
    m3 = match_task_gemini("Mujhe form bharna hai", candidates)
    assert m3.task_id is None
    assert m3.status == "ambiguous"
    assert m3.needs_clarification is True

    # No match
    m4 = match_task_gemini("Weather forecast today", candidates)
    assert m4.task_id is None
    assert m4.status == "no_match"

# Test Field Extraction & No Guessing
def test_field_extraction_rules():
    fields = [
        FieldDefinition(id="child_name", label="Child's full name", type="TEXT", required=True),
        FieldDefinition(id="date_of_birth", label="Date of birth", type="DATE_TEXT", required=True),
        FieldDefinition(id="place_of_birth", label="Place of birth", type="TEXT", required=True),
        FieldDefinition(id="father_name", label="Father's full name", type="TEXT", required=True),
        FieldDefinition(id="mother_name", label="Mother's full name", type="TEXT", required=True),
    ]

    transcript = "Bacchi ka naam Aarav Sharma hai, janam 12 March 2024 ko hua, jagah Prayagraj, pita Ramesh Sharma aur mata Sunita Sharma."
    res = extract_fields_gemini("hi-IN", transcript, fields)

    extracted_dict = {v.field_id: v.value for v in res.values}
    assert extracted_dict.get("child_name") == "Aarav Sharma"
    assert extracted_dict.get("date_of_birth") == "12-03-2024"
    assert extracted_dict.get("place_of_birth") == "Prayagraj"
    assert extracted_dict.get("father_name") == "Ramesh Sharma"
    assert extracted_dict.get("mother_name") == "Sunita Sharma"
    assert len(res.missing_field_ids) == 0

    # Missing field test: Transcript without mother's name
    transcript_partial = "Bacchi ka naam Aarav Sharma hai, janam 12 March 2024 ko hua, jagah Prayagraj, pita Ramesh Sharma."
    res_partial = extract_fields_gemini("hi-IN", transcript_partial, fields)
    extracted_partial_dict = {v.field_id: v.value for v in res_partial.values}
    assert "mother_name" not in extracted_partial_dict
    assert "mother_name" in res_partial.missing_field_ids

    # Ask for missing field only: one-at-a-time test
    mother_field_only = [FieldDefinition(id="mother_name", label="Mother's full name", type="TEXT", required=True)]
    res_single = extract_fields_gemini("hi-IN", "Mata Sunita Sharma", mother_field_only)
    assert len(res_single.values) == 1
    assert res_single.values[0].field_id == "mother_name"
    assert res_single.values[0].value == "Sunita Sharma"

# Test Say-to-Fill Normalisation
def test_normalize_value():
    # Email normalization
    r_email = normalize_value_gemini("en-IN", "EMAIL", "ramesh at gmail dot com")
    assert r_email.normalized_value == "ramesh@gmail.com"
    assert r_email.status == "normalized"

    # Numeric phone normalization
    r_phone = normalize_value_gemini("en-IN", "PHONE", "nine eight seven six five four three two one zero")
    assert r_phone.normalized_value == "9876543210"
    assert r_phone.status == "normalized"

    # Date normalization
    r_date = normalize_value_gemini("en-IN", "DATE_TEXT", "15th August 2024")
    assert r_date.normalized_value == "15-08-2024"
    assert r_date.status == "normalized"
