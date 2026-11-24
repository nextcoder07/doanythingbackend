# Privacy Guarantees, Security Architecture, and System Limitations

## 1. Product Contract and Non-Negotiable Boundaries

Hakdaar is an assistive voice co-pilot designed to guide a single user through public form-filling tasks on Android. It operates under a strict "clerk looking away" security model:

1. **No User Authentication or Accounts**: There are no user accounts, no registration, no Firebase Authentication, no Supabase Authentication, and no multi-user architecture.
2. **Zero Persistent Storage of Personal Data**:
   - No Room database, SQLite, PostgreSQL, Firestore, or cloud database.
   - No task history, chat logs, conversation records, or audio recordings are saved to disk or persistent cloud buckets.
   - Android DataStore is used **exclusively** for non-sensitive preferences (preferred language code, TTS toggle, speech rate).
3. **No Passive or Background Listening**:
   - Audio capture is explicitly initiated by the user tapping "Record" within the visible `VoiceCaptureActivity`.
   - Audio recording is capped at 55 seconds.
   - Audio buffers exist purely in RAM and are immediately wiped with zeroisation (`fill(0)`) upon completion, cancellation, or error.
4. **No Autonomous Agent Operation**:
   - Hakdaar cannot arbitrarily click buttons, navigate arbitrary menus, or invent form workflows.
   - All guided tasks, page signatures, field selectors, and steps are strictly human-authored in the bundled `task_catalog.json`.
   - The user physically navigates, physically focuses each field, and confirms every individual text fill.
5. **No Access to Secrets or Payments**:
   - Hakdaar never reads or fills passwords, passcodes, PINs, OTPs, CVVs, UPI PINs, CAPTCHAs, or biometric prompts.
   - Hakdaar never presses Submit, Apply, Pay, Sign, or final-declaration buttons.

---

## 2. Sensitive Screen Detection and Handover Protocol

Hakdaar enforces a **fail-closed** policy. Whenever a sensitive screen or field is detected:
- The floating highlight is immediately removed.
- Audio recording stops and pending audio buffers are zeroed out.
- Any pending fill confirmation is rejected.
- The bubble transitions to `HANDOVER` state:
  > **"Hakdaar is looking away for this step."**
  > Reason: Password / PIN / OTP / Payment / CAPTCHA / Final submit
- Hakdaar remains paused until the user completes the action themselves and taps **"I am done — Resume"**.

### Supplemental Blocklists
Hakdaar maintains a bundled `blocked_targets.json` that includes:
- Known Indian banking and UPI apps (PhonePe, Paytm, Google Pay, BHIM, Yono SBI, iMobile, HDFC Mobile, Axis Mobile, Kotak Bank, Kite, Groww, etc.).
- Known payment and banking domains (`onlinesbi.sbi`, `netbanking.hdfcbank.com`, `icicibank.com`, `razorpay.com`, etc.).
- 31 English sensitive keywords and 20 Hindi sensitive keywords.

---

## 3. Accessibility Service Boundaries

- `canPerformGestures` is set to `false`. Hakdaar never calls `dispatchGesture` to simulate touches or taps.
- Hakdaar never captures screenshots (`MediaProjection`), screen recordings, or clipboard data.
- Hakdaar never inspects the current text content of editable fields. It inspects only static metadata (labels, hints, content descriptions, input types, and visible screen bounds).
- Text insertion is performed exclusively through Android Accessibility `ACTION_SET_TEXT`. If an app does not support `ACTION_SET_TEXT`, Hakdaar stops safely and instructs the user to type manually.

---

## 4. In-Memory Session Lifecycle & RAM Wiping

All in-flight form details exist inside an in-memory `ActiveTaskSession` object.
- **Session Wiping Triggers**: Task completion, user cancellation, timeout, sensitive screen abort, or Android process death.
- **RAM Hygiene**:
  - Byte arrays storing audio are filled with zeros (`fill(0)`) before releasing references.
  - Mutable maps and lists holding extracted field values are explicitly cleared.
  - While Kotlin/JVM does not guarantee immediate physical hardware zeroisation of garbage-collected strings, Hakdaar guarantees zero persistent disk storage and zero caching across sessions.
- **Target App Warning**:
  - Hakdaar cannot delete values already typed into a third-party website or application.
  - When a session ends, Hakdaar clearly reminds the user: *"The target app or website may still display values you entered. Clear or close that page yourself if you do not want them left on screen."*

---

## 5. Cloud Service & Network Limitations

1. **Public Prototype Endpoint**: The Cloud Run backend is unauthenticated for this prototype. To mitigate abuse, the service enforces:
   - Strict payload size limits (< 1.8 MB).
   - Strict audio duration limits (≤ 55 seconds).
   - WAV header structural verification.
   - Strict Pydantic schema validation.
2. **Privacy-Safe Backend Logging**:
   - Hakdaar's backend never logs request bodies, transcripts, audio payloads, or extracted field JSON.
   - Logs contain only: timestamp, request ID, endpoint path, HTTP status, and duration in milliseconds.
3. **Google Speech-to-Text & Gemini API Processing**:
   - Audio is transmitted to Google Cloud Speech-to-Text V2 (`chirp_2` in `asia-southeast1`).
   - Transcripts are sent to the Gemini Developer API (`gemini-3.8-flash`) using structured JSON mode.
   - Use of the **paid Gemini API tier** is required for handling personal data, ensuring prompts are not used to train models under Google's standard enterprise terms.
4. **Demo Portal Notice**:
   - The demo portal pages (`/demo/birth-certificate` and `/demo/widow-pension`) are simulated local prototypes.
   - Form submission is intercepted locally via `event.preventDefault()` in JavaScript. No data is stored, transmitted to any database, or submitted to government authorities.
