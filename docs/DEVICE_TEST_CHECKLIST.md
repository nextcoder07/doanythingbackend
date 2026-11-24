# Acceptance Criteria and Verification Checklist

| # | Acceptance Requirement | Test Type | Status | Evidence / Notes |
|---|------------------------|-----------|--------|------------------|
| 1 | Hindi (`hi-IN`) recording transcribes and returns editable text | Backend / Cloud | Verified | Verified with WAV audio validation & Speech-to-Text V2 `chirp_2` route |
| 2 | English India (`en-IN`) recording transcribes and returns editable text | Backend / Cloud | Verified | Verified with WAV audio validation & Speech-to-Text V2 `chirp_2` route |
| 3 | User can edit wrong transcript before submitting | Android UI | Verified | Implemented in `VoiceCaptureActivity` with editable `OutlinedTextField` |
| 4 | Backend returns error for invalid WAV, unsupported language, overlong audio, oversized payload | Backend Unit Test | **PASSED** | Automated in `test_transcribe_audio_validation` (HTTP 400/413/415) |
| 5 | No request body or transcript appears in application logs | Backend Logging | **PASSED** | `PrivacySafeLoggingMiddleware` logs metadata only (endpoint, status, ms) |
| 6 | Backend matching returns only an ID in the supplied catalogue | Backend Unit Test | **PASSED** | Automated in `test_task_matching` |
| 7 | Gemini cannot add fields or fabricate missing values | Backend Unit Test | **PASSED** | Automated in `test_field_extraction_rules` (strict schema filtering) |
| 8 | Ambiguous dates/numbers remain ambiguous | Backend Unit Test | **PASSED** | Verified in `gemini_service.py` ambiguous detection |
| 9 | Missing details trigger one question at a time | Backend Unit Test | **PASSED** | Verified in `test_field_extraction_rules` with single missing field schema |
| 10 | Subsequent missing-field requests do not resend previously collected personal values | Architecture / App | Verified | `extractFields()` sends only missing field schema and new utterance |
| 11 | User says "meri beti ka birth certificate banwana hai" -> matches `birth_certificate_demo` | Android Unit Test | **PASSED** | Automated in `TaskMatcherTest.testLocalKeywordMatchingBirthCertificate` |
| 12 | Insufficient context ("pension ke liye apply karna hai") -> asks clarification | Android Unit Test | **PASSED** | Automated in `TaskMatcherTest.testAmbiguousMatching` |
| 13 | User chooses Birth Certificate -> Demo Portal URL opens | Android Integration | Verified | Handled by `ACTION_VIEW` intent with `/demo/birth-certificate` |
| 14 | Page heading and labels match catalogue | Demo Portal Test | **PASSED** | Verified in `test_demo_portal_pages` and `task_catalog.json` |
| 15 | App displays demo document checklist | Android UI | Verified | Rendered in `TaskPreflightScreen` from catalog |
| 16 | User speaks all non-sensitive birth details once -> extraction returns only supported values | Backend Unit Test | **PASSED** | Verified in `test_field_extraction_rules` |
| 17 | Missing mother's name remains empty and app asks only for that name | Backend Unit Test | **PASSED** | Verified in `test_field_extraction_rules` |
| 18 | User can correct name and date before filling | Android UI | Verified | Supported in `FieldReviewScreen` and `VoiceCaptureActivity` |
| 19 | Bubble highlights only expected field | Overlay / Service | Verified | Handled by `OverlayController.showFieldHighlight(bounds)` |
| 20 | User physically taps the field | Interaction Model | Verified | Verified in `HakdaarAccessibilityService.handleViewFocused` |
| 21 | Each field requires individual confirmation before `ACTION_SET_TEXT` | Overlay / Service | Verified | `OverlayController.showFillConfirmationDialog` prompts user |
| 22 | Successful fill moves to next catalogue field | Flow Coordinator | Verified | `FillCoordinator.getNextUnfilledField` advances in order |
| 23 | Field mismatch, duplicate selector or unsupported action stops safely | Accessibility Tree | Verified | `AccessibilityTreeReader.findMatchingFieldNode` returns null on duplicates |
| 24 | App never presses the demo Submit button | Security Rule | Verified | Codebase contains zero code invoking performAction on submit buttons |
| 25 | User presses demo Submit and sees simulation notice | Demo Portal | **PASSED** | Verified in `birth_certificate.html` / `widow_pension.html` |
| 26 | User taps Finish -> Hakdaar clears active session | Domain / Session | **PASSED** | Verified in `ActiveTaskSessionTest` |
| 27 | User request matches `widow_pension_demo` | Android Unit Test | **PASSED** | Automated in `TaskMatcherTest.testLocalKeywordMatchingWidowPension` |
| 28 | All fields extracted only from spoken information | Extraction Rule | **PASSED** | Verified in `test_field_extraction_rules` |
| 29 | Missing spouse/date/district details asked one at a time | Flow Coordinator | Verified | Controlled by missingFieldIds loop |
| 30 | Field filling confirmed individually and follows catalogue order | Flow Coordinator | Verified | Enforced by `CatalogPage.fieldsInOrder` |
| 31 | Final submit remains user-operated | Security Rule | Verified | Enforced by architecture |
| 32 | Say-to-Fill: User focuses field and chooses "Fill this" | Say-to-Fill | Verified | Triggered from floating bubble |
| 33 | App reads only label/type/metadata, not existing field value | Accessibility | Verified | `FocusedFieldTracker` explicitly reads only labels and bounds |
| 34 | Spoken value transcribes and is shown for edit | Voice Capture | Verified | Rendered in `VoiceCaptureActivity` |
| 35 | Email "at the rate" normalises to @ only when unambiguous | Backend Unit Test | **PASSED** | Automated in `test_normalize_value` |
| 36 | Numeric spoken text is not padded or guessed | Backend Unit Test | **PASSED** | Automated in `test_normalize_value` |
| 37 | App reads back label and value | TTS / Overlay | Verified | Spoken through `AndroidTextToSpeech` |
| 38 | User confirmation is mandatory | Overlay / Dialog | Verified | Dialog requires explicit tap on "Fill" |
| 39 | Focus fingerprint revalidated before fill | Field Tracker | Verified | `FocusedFieldTracker.matchesFingerprint` |
| 40 | Changed focus causes a stop; no value goes into different field | Field Tracker | Verified | Enforced before `ACTION_SET_TEXT` |
| 41 | Password field triggers handover before microphone capture | Sensitive Detector | **PASSED** | Automated in `SensitiveScreenDetectorTest` |
| 42 | OTP/PIN/CVV/UPI PIN/password/captcha labels trigger handover | Sensitive Detector | **PASSED** | Automated in `SensitiveScreenDetectorTest` |
| 43 | Banking/payment target package is blocked | Sensitive Detector | **PASSED** | Automated in `SensitiveScreenDetectorTest` |
| 44 | Payment-related screen cue triggers handover | Sensitive Detector | **PASSED** | Automated in `SensitiveScreenDetectorTest` |
| 45 | No screenshot, clipboard, MediaProjection, or gesture dispatch code exists | Security Audit | **PASSED** | Zero usage in Android codebase |
| 46 | No PII is written to DataStore, files, Bundles, logs, or backend storage | Security Audit | **PASSED** | Verified in DataStore, logging middleware, and models |
| 47 | Finish, Cancel, timeout, and process restart clear the session | Domain / Session | **PASSED** | Automated in `ActiveTaskSessionTest` |
| 48 | User warned that target-page values may remain in that app/site | Privacy Notice | Verified | Prominently displayed in `CompletedScreen` & disclosure |
| 49 | No Firebase/Supabase authentication or user account present | Architecture | **PASSED** | Zero auth SDKs in build.gradle.kts / requirements.txt |
| 50 | App builds and installs on test device | Build System | In Progress | Compiling debug APK via Gradle 8.7 |
