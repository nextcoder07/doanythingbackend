# Cloud Setup and Deployment Guide

## 1. Google Cloud Architecture

Hakdaar relies on two cloud capabilities:
1. **Google Cloud Speech-to-Text V2** with the `chirp_2` foundation model hosted in region `asia-southeast1`.
2. **Google Gen AI API** using `gemini-3.8-flash` with structured JSON output for task matching and field extraction.

Both services are called exclusively from the stateless Python FastAPI backend (`hakdaar-api`) deployed to Google Cloud Run. **No API keys or cloud credentials ever exist inside the Android APK.**

---

## 2. Google Cloud Setup Checklist

### Step 1: Create GCP Project and Enable APIs
```bash
gcloud projects create hakdaar-prototype --name="Hakdaar Prototype"
gcloud config set project hakdaar-prototype

# Enable required APIs
gcloud services enable \
    run.googleapis.com \
    speech.googleapis.com \
    secretmanager.googleapis.com \
    artifactregistry.googleapis.com \
    cloudbuild.googleapis.com
```

### Step 2: Speech-to-Text V2 Recognition and Chirp 2 Entitlement
The `chirp_2` model in `asia-southeast1` provides Hindi (`hi-IN`) and Indian English (`en-IN`) speech recognition:
- Verify that your GCP project has access to `chirp_2` in `asia-southeast1`.
- If your project is not entitled, the backend will return `CHIRP2_ACCESS_DENIED` with exact entitlement details. Hakdaar strictly refuses silent fallbacks.

### Step 3: Service Account & IAM Roles
Create a dedicated runtime service account for Cloud Run:
```bash
gcloud iam service-accounts create hakdaar-runtime \
    --display-name="Hakdaar Cloud Run Runtime Identity"

# Grant Speech client permission
gcloud projects add-iam-policy-binding hakdaar-prototype \
    --member="serviceAccount:hakdaar-runtime@hakdaar-prototype.iam.gserviceaccount.com" \
    --role="roles/speech.client"
```

### Step 4: Gemini API Key in Secret Manager
Generate a Gemini API key from Google AI Studio (ensure billing is enabled on the associated project for the paid tier). Store it securely in Secret Manager:
```bash
echo -n "YOUR_GEMINI_API_KEY" | gcloud secrets create GEMINI_API_KEY \
    --data-file=- \
    --replication-policy="automatic"

# Grant Secret Accessor role to the Cloud Run runtime account
gcloud secrets add-iam-policy-binding GEMINI_API_KEY \
    --member="serviceAccount:hakdaar-runtime@hakdaar-prototype.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
```

---

## 3. Backend Deployment to Google Cloud Run

Deploy the container directly from source:
```bash
cd backend

gcloud run deploy hakdaar-api \
    --source . \
    --region asia-south1 \
    --service-account hakdaar-runtime@hakdaar-prototype.iam.gserviceaccount.com \
    --set-env-vars GCP_PROJECT_ID=hakdaar-prototype,STT_LOCATION=asia-southeast1,STT_MODEL=chirp_2,GEMINI_MODEL_ID=gemini-3.8-flash,MAX_AUDIO_SECONDS=55,MAX_AUDIO_BYTES=1800000 \
    --set-secrets GEMINI_API_KEY=GEMINI_API_KEY:latest \
    --allow-unauthenticated \
    --max-instances 5
```

Once deployed, note the service URL (e.g., `https://hakdaar-api-xyz.a.run.app`).

### Verify Deployment
```bash
curl https://hakdaar-api-xyz.a.run.app/healthz
# Expected: {"status":"ok","service":"hakdaar-api"}
```

---

## 4. Local Backend Development

To run the backend locally:
```bash
cd backend
python -m venv venv
venv\Scripts\activate  # On Windows
pip install -r requirements.txt

# Copy example environment file
cp .env.example .env
# Edit .env with your GCP project ID and Gemini API key

uvicorn app.main:app --host 0.0.0.0 --port 8080 --reload
```

---

## 5. Android Application Configuration

Open `android/app/build.gradle.kts`:
- Update `HAKDAAR_API_BASE_URL` with your deployed Cloud Run URL or `http://10.0.2.2:8080` for the Android emulator.
- Update `HAKDAAR_DEMO_BASE_URL` similarly.

Build and install the debug APK:
```bash
cd android
.\gradlew.bat assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk
```
