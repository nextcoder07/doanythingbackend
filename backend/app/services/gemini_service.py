import json
import re
from typing import List, Optional, Tuple
from fastapi import HTTPException
from app.config import settings
from app.schemas import (
    TaskCandidate, MatchTaskResponse,
    FieldDefinition, ExtractFieldsResponse, ExtractedValue,
    NormalizeValueResponse
)

def get_gemini_client():
    if not settings.GEMINI_API_KEY:
        return None
    try:
        from google import genai
        return genai.Client(api_key=settings.GEMINI_API_KEY)
    except Exception:
        return None

def match_task_gemini(transcript: str, candidates: List[TaskCandidate]) -> MatchTaskResponse:
    """
    Matches transcript against known candidates using Gemini or safe deterministic fallback.
    Accepts ONLY an ID that is present in the supplied candidates. Never invents a task.
    """
    clean_transcript = transcript.strip().lower()
    candidate_dict = {c.id: c for c in candidates}
    candidate_ids = list(candidate_dict.keys())

    # Deterministic matching rules first
    # Check for birth certificate keywords
    birth_keywords = ["birth", "janam", "janm", "bachi", "bacche", "beti", "beta", "certificate", "प्रमाण पत्र"]
    pension_keywords = ["pension", "widow", "vidhwa", "pati", "decease", "death", "पेंशन", "विधवा"]

    matches_birth = any(kw in clean_transcript for kw in birth_keywords)
    matches_pension = any(kw in clean_transcript for kw in pension_keywords)

    if matches_birth and not matches_pension and "birth_certificate_demo" in candidate_dict:
        return MatchTaskResponse(
            task_id="birth_certificate_demo",
            status="matched",
            needs_clarification=False
        )
    if matches_pension and not matches_birth and "widow_pension_demo" in candidate_dict:
        return MatchTaskResponse(
            task_id="widow_pension_demo",
            status="matched",
            needs_clarification=False
        )
    if matches_birth and matches_pension:
        return MatchTaskResponse(
            task_id=None,
            status="ambiguous",
            needs_clarification=True,
            clarification_question="Did you want Birth Certificate Application or Widow Pension Application?"
        )

    # Use Gemini API if configured
    client = get_gemini_client()
    if client:
        try:
            candidate_list_text = "\n".join([f"- id: {c.id}, title: {c.title}, description: {c.description}" for c in candidates])
            prompt = f"""You are a strict task classification system for Hakdaar.
Candidate Tasks:
{candidate_list_text}

User transcript: "{transcript}"

Rules:
1. Match the transcript ONLY to one of the candidate IDs above.
2. If the transcript clearly matches a candidate, return status="matched" and the exact task_id.
3. If it is ambiguous between multiple candidates or unclear, return status="ambiguous", task_id=null, needs_clarification=true, and a short clarification_question in the user's language.
4. If no candidate matches, return status="no_match", task_id=null.
5. NEVER invent a new task ID. Only choose from: {candidate_ids}.
6. Output JSON only. Format:
{{"task_id": "...", "status": "matched"|"ambiguous"|"no_match", "needs_clarification": true|false, "clarification_question": null|"..."}}
"""
            response = client.models.generate_content(
                model=settings.GEMINI_MODEL_ID,
                contents=prompt,
                config={"response_mime_type": "application/json"}
            )
            raw_text = response.text
            parsed = json.loads(raw_text)
            matched_id = parsed.get("task_id")
            if matched_id and matched_id not in candidate_dict:
                # Disallow any non-candidate ID!
                matched_id = None
                parsed["status"] = "no_match"
            return MatchTaskResponse(
                task_id=matched_id,
                status=parsed.get("status", "no_match"),
                needs_clarification=parsed.get("needs_clarification", False),
                clarification_question=parsed.get("clarification_question")
            )
        except Exception:
            # Fall back to ambiguous/no_match safely
            pass

    # Safe fallback if no match and no Gemini
    return MatchTaskResponse(
        task_id=None,
        status="ambiguous" if ("apply" in clean_transcript or "form" in clean_transcript or "karna hai" in clean_transcript) else "no_match",
        needs_clarification=True if ("apply" in clean_transcript or "form" in clean_transcript or "karna hai" in clean_transcript) else False,
        clarification_question="Please select which task you would like to start: Birth Certificate or Widow Pension."
    )

def extract_fields_gemini(locale: str, transcript: str, fields: List[FieldDefinition]) -> ExtractFieldsResponse:
    """
    Extracts non-sensitive values from the transcript based on the field definitions.
    Strictly forbids guessing or filling default values.
    Includes source quotes.
    """
    valid_field_ids = {f.id for f in fields}
    field_descriptions = "\n".join([
        f"- id: {f.id}, label: {f.label}, type: {f.type}, required: {f.required}, format: {f.format or 'text'}"
        for f in fields
    ])

    client = get_gemini_client()
    if client:
        try:
            prompt = f"""You are the strict field extraction engine for Hakdaar.
You must extract field values from the user's spoken transcript.
Locale: {locale}
Transcript: "{transcript}"

Target Fields:
{field_descriptions}

Rules:
1. Extract values ONLY if they are explicitly stated in the transcript.
2. NEVER guess, invent, or assume any value. If a field was not mentioned in the transcript, DO NOT include it in values.
3. Every extracted value must include an exact `source_quote` substring from the transcript showing where it was heard.
4. For DATE_TEXT, format as DD-MM-YYYY if day, month, and year are clearly stated. If ambiguous, do not extract; add field_id to ambiguous_field_ids.
5. Preserve names as spoken. Do not alter or translate names.
6. Only return field_ids from the allowed list: {list(valid_field_ids)}.
7. List any required fields that were NOT mentioned in missing_field_ids.
8. Output JSON only matching this schema:
{{
  "values": [
    {{"field_id": "...", "value": "...", "source_quote": "...", "status": "extracted"}}
  ],
  "missing_field_ids": ["..."],
  "ambiguous_field_ids": ["..."]
}}
"""
            response = client.models.generate_content(
                model=settings.GEMINI_MODEL_ID,
                contents=prompt,
                config={"response_mime_type": "application/json"}
            )
            parsed = json.loads(response.text)
            
            # Validate and filter strictly
            valid_extracted: List[ExtractedValue] = []
            extracted_ids = set()
            for v in parsed.get("values", []):
                fid = v.get("field_id")
                val = str(v.get("value", "")).strip()
                quote = str(v.get("source_quote", "")).strip()
                if fid in valid_field_ids and val:
                    valid_extracted.append(ExtractedValue(
                        field_id=fid,
                        value=val,
                        source_quote=quote,
                        status="extracted"
                    ))
                    extracted_ids.add(fid)

            missing = [f.id for f in fields if f.required and f.id not in extracted_ids and f.id not in parsed.get("ambiguous_field_ids", [])]
            ambiguous = [fid for fid in parsed.get("ambiguous_field_ids", []) if fid in valid_field_ids]

            return ExtractFieldsResponse(
                values=valid_extracted,
                missing_field_ids=missing,
                ambiguous_field_ids=ambiguous
            )
        except Exception:
            pass

    # Deterministic rule-based extraction fallback for testing & offline mode
    extracted_values: List[ExtractedValue] = []
    extracted_ids = set()
    ambiguous_ids = []

    # Simple heuristic extraction when Gemini API key is not present or for standard tests
    # Name patterns:
    # "naam <Name> hai" / "name is <Name>"
    # "pita <Father> aur mata <Mother>"
    # "janam <Date>"
    # "jagah <Place>"
    t = transcript

    for field in fields:
        val = None
        quote = None
        if field.id in ["child_name", "applicant_name"]:
            m = re.search(r"(?:naam|name is|naam hai)\s+([A-Za-z\u0900-\u097F\s]+?)(?=\s*(?:hai|janam|born|pita|father|mata|aur|,|\.|$))", t, re.IGNORECASE)
            if m:
                val = m.group(1).strip()
                quote = m.group(0).strip()
        elif field.id in ["father_name", "spouse_name"]:
            m = re.search(r"(?:pita|father|spouse|pati)\s+([A-Za-z\u0900-\u097F\s]+?)(?=\s*(?:aur|and|mata|mother|janam|hai|,|\.|$))", t, re.IGNORECASE)
            if m:
                val = m.group(1).strip()
                quote = m.group(0).strip()
        elif field.id == "mother_name":
            m = re.search(r"(?:mata|mother)\s+([A-Za-z\u0900-\u097F\s]+?)(?=\s*(?:hai|,|\.|$))", t, re.IGNORECASE)
            if m:
                val = m.group(1).strip()
                quote = m.group(0).strip()
        elif field.id in ["date_of_birth", "date_of_death"]:
            # e.g., 12 March 2024 or 12-03-2024
            m = re.search(r"(\d{1,2}(?:st|nd|rd|th)?\s+(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{4})", t, re.IGNORECASE)
            if m:
                raw_date = m.group(1).strip()
                quote = m.group(0).strip()
                try:
                    from datetime import datetime
                    clean_date_str = re.sub(r"(\d+)(?:st|nd|rd|th)", r"\1", raw_date)
                    # Support both full and abbreviated month names
                    try:
                        dt = datetime.strptime(clean_date_str, "%d %B %Y")
                    except ValueError:
                        dt = datetime.strptime(clean_date_str, "%d %b %Y")
                    val = dt.strftime("%d-%m-%Y")
                except Exception:
                    val = raw_date
            else:
                m2 = re.search(r"(\d{1,2}[-/]\d{1,2}[-/]\d{4})", t)
                if m2:
                    val = m2.group(1).replace("/", "-")
                    quote = m2.group(0)
        elif field.id in ["place_of_birth", "district", "address"]:
            m = re.search(r"(?:jagah|place|district|address|rehte hain)\s+([A-Za-z\u0900-\u097F\s]+?)(?=\s*(?:hai|pita|mata|,|\.|$))", t, re.IGNORECASE)
            if m:
                val = m.group(1).strip()
                quote = m.group(0).strip()

        if val and quote:
            extracted_values.append(ExtractedValue(
                field_id=field.id,
                value=val,
                source_quote=quote,
                status="extracted"
            ))
            extracted_ids.add(field.id)

    missing = [f.id for f in fields if f.required and f.id not in extracted_ids]
    return ExtractFieldsResponse(
        values=extracted_values,
        missing_field_ids=missing,
        ambiguous_field_ids=ambiguous_ids
    )

def normalize_value_gemini(locale: str, input_type: str, transcript: str) -> NormalizeValueResponse:
    """
    Normalizes a spoken single-field value (e.g. Say-to-Fill mode).
    Converts unambiguous spoken patterns like "at the rate" -> "@", "dot" -> ".", digit sequences.
    Never infers missing digits or names.
    """
    clean = transcript.strip()
    upper_type = input_type.upper()

    # Deterministic normalizations
    if "EMAIL" in upper_type:
        norm = clean.lower()
        norm = re.sub(r"\s+at\s+the\s+rate\s+", "@", norm)
        norm = re.sub(r"\s+at\s+rate\s+", "@", norm)
        norm = re.sub(r"\s+at\s+", "@", norm)
        norm = re.sub(r"\s+dot\s+", ".", norm)
        norm = norm.replace(" ", "")
        if "@" in norm and "." in norm:
            return NormalizeValueResponse(normalized_value=norm, status="normalized", needs_clarification=False)
        else:
            return NormalizeValueResponse(normalized_value=norm, status="ambiguous", needs_clarification=True)

    if "PHONE" in upper_type or "NUMBER" in upper_type:
        # Map spoken Hindi/English digits to numbers
        word_to_digit = {
            "zero": "0", "one": "1", "two": "2", "three": "3", "four": "4",
            "five": "5", "six": "6", "seven": "7", "eight": "8", "nine": "9",
            "शून्य": "0", "एक": "1", "दो": "2", "तीन": "3", "चार": "4",
            "पांच": "5", "छह": "6", "सात": "7", "आठ": "8", "नौ": "9"
        }
        tokens = clean.split()
        converted = []
        is_digit_sequence = True
        for tok in tokens:
            lower_tok = tok.lower()
            if lower_tok in word_to_digit:
                converted.append(word_to_digit[lower_tok])
            elif tok.isdigit():
                converted.append(tok)
            else:
                is_digit_sequence = False
                break
        
        if is_digit_sequence and converted:
            num_str = "".join(converted)
            return NormalizeValueResponse(normalized_value=num_str, status="normalized", needs_clarification=False)

    if "DATE" in upper_type:
        m = re.search(r"(\d{1,2}(?:st|nd|rd|th)?\s+[A-Za-z]+\s+\d{4})", clean)
        if m:
            try:
                from datetime import datetime
                clean_str = re.sub(r"(\d+)(?:st|nd|rd|th)", r"\1", m.group(1))
                try:
                    dt = datetime.strptime(clean_str, "%d %B %Y")
                except ValueError:
                    dt = datetime.strptime(clean_str, "%d %b %Y")
                return NormalizeValueResponse(normalized_value=dt.strftime("%d-%m-%Y"), status="normalized", needs_clarification=False)
            except Exception:
                pass

    # Use Gemini if available
    client = get_gemini_client()
    if client:
        try:
            prompt = f"""Normalize this single spoken field value for input type: {input_type}.
Locale: {locale}
Spoken transcript: "{clean}"

Rules:
1. Convert unambiguous formatting (e.g. "at the rate" to @, "dot" to ., spoken digits to numbers).
2. DO NOT guess missing information, missing digits, or invent suffixes.
3. If the value is ambiguous or incomplete, set status="ambiguous" and needs_clarification=true.
4. Output JSON: {{"normalized_value": "...", "status": "normalized"|"ambiguous", "needs_clarification": true|false}}
"""
            response = client.models.generate_content(
                model=settings.GEMINI_MODEL_ID,
                contents=prompt,
                config={"response_mime_type": "application/json"}
            )
            parsed = json.loads(response.text)
            return NormalizeValueResponse(
                normalized_value=parsed.get("normalized_value", clean),
                status=parsed.get("status", "normalized"),
                needs_clarification=parsed.get("needs_clarification", False)
            )
        except Exception:
            pass

    return NormalizeValueResponse(
        normalized_value=clean,
        status="normalized",
        needs_clarification=False
    )
