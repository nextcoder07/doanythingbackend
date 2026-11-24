# Hakdaar — Assistive Voice Co-Pilot for Form-Filling

> **Hakdaar** is a voice co-pilot that floats over an Android phone. It guides a person through a form-filling task, asks for the required details by voice, prepares and confirms each value, and fills eligible fields. The person always handles login, passwords, OTPs, PINs, captchas, payments, declarations, signatures, and final submission themselves.

---

## 1. System Architecture

Hakdaar consists of three tightly coupled components:
1. **Android Application (`/android`)**:
   - Native Android app written in Kotlin with Jetpack Compose & Material 3.
   - Accessibility Service with `TYPE_ACCESSIBILITY_OVERLAY` for the non-blocking floating bubble and field highlight rectangle.
   - In-memory `ActiveTaskSession` (zero disk database, zero persistent form data).
   - Audio capture via `AudioRecord` (16 kHz, mono, PCM 16-bit WAV in RAM; max 55 seconds).
   - Local Text-To-Speech read-back and spoken instructions.
   - Bundled static catalogue (`task_catalog.json`) and blocked targets list (`blocked_targets.json`).
2. **Stateless Backend API (`/backend`)**:
   - Python 3.12+ FastAPI service designed for Google Cloud Run.
   - Synchronous Speech-to-Text V2 (`chirp_2` in `asia-southeast1`) for Hindi (`hi-IN`) and Indian English (`en-IN`).
   - Gemini Developer API (`gemini-3.8-flash`) for structured task matching, field extraction, and value normalisation.
   - Privacy-safe logging (never logs request bodies, transcripts, form values, or audio).
3. **Demo Portal (`/backend/app/demo_portal`)**:
   - Static accessible HTML demo forms for Birth Certificate Application and Widow Pension Application.
   - Accessible labels, inputs, and a simulated submit button that prevents default and alerts the user that no government data was saved.

---

## 2. Working Modes

1. **Guided Mode**:
   - User speaks their need (e.g., *"Meri beti ka birth certificate banwana hai"*).
   - Matched strictly against the APK's bundled catalogue.
   - User speaks non-sensitive details in one utterance.
   - Gemini extracts values with source quotes without guessing.
   - Missing fields are asked one at a time.
   - Details are reviewed by the user.
   - Hakdaar highlights each field, user taps it, Hakdaar confirms the value, and fills it using `ACTION_SET_TEXT`.
2. **Say-to-Fill Mode**:
   - User focuses any eligible text field in a supported app/site, taps the Hakdaar bubble, and taps "Fill this".
   - Hakdaar reads only field metadata (label and input type).
   - User speaks the value, reviews it, and Hakdaar normalises and fills that single focused field.

---

## 3. Quick Start: Running the Backend & Demo Portal

### Prerequisites
- Python 3.12+
- (Optional) Google Cloud Project with Cloud Speech-to-Text V2 and Gemini API Key

### Start the Local Server
```bash
cd backend
python -m venv venv
venv\Scripts\activate  # Windows
pip install -r requirements.txt

# Run the FastAPI server
uvicorn app.main:app --host 0.0.0.0 --port 8080 --reload
```

### Access the Demo Portal
Open in your browser:
- Birth Certificate Demo: `http://localhost:8080/demo/birth-certificate`
- Widow Pension Demo: `http://localhost:8080/demo/widow-pension`

---

## 4. Quick Start: Building the Android App

### Prerequisites
- JDK 17
- Android SDK (compileSdk 34)

### Build Debug APK
```bash
cd android
.\gradlew.bat assembleDebug
```
The APK will be generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

### Install on Device or Emulator
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Non-Negotiable Privacy and Security Invariants

- **No User Accounts or Passwords**: Single-user hackathon prototype.
- **Zero Database Persistence**: All task details, transcripts, and audio buffers are discarded on completion, cancellation, timeout, or app termination.
- **Fail-Closed Sensitive Screen Detection**: The bubble immediately looks away (enters `HANDOVER` state) when encountering passwords, OTPs, PINs, CVVs, CAPTCHAs, payment screens, or banking apps.
- **No Autonomous Submissions**: Hakdaar never presses Submit, Apply, Pay, or Sign.

For full architectural details, see [docs/PRIVACY_AND_LIMITATIONS.md](file:///c:/Users/creat/Documents/webdev_project/scaler2/docs/PRIVACY_AND_LIMITATIONS.md).
