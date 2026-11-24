from typing import List, Optional
from pydantic import BaseModel, Field

class HealthResponse(BaseModel):
    status: str = "ok"
    service: str = "hakdaar-api"

class TranscribeResponse(BaseModel):
    transcript: str
    language_code: str
    status: str = "ok"

class TaskCandidate(BaseModel):
    id: str
    title: str
    description: str

class MatchTaskRequest(BaseModel):
    transcript: str
    candidates: List[TaskCandidate]

class MatchTaskResponse(BaseModel):
    task_id: Optional[str] = None
    status: str  # "matched", "ambiguous", "no_match"
    needs_clarification: bool = False
    clarification_question: Optional[str] = None

class FieldDefinition(BaseModel):
    id: str
    label: str
    type: str
    required: bool = True
    format: Optional[str] = None

class ExtractFieldsRequest(BaseModel):
    locale: str
    transcript: str
    fields: List[FieldDefinition]

class ExtractedValue(BaseModel):
    field_id: str
    value: str
    source_quote: str
    status: str = "extracted"

class ExtractFieldsResponse(BaseModel):
    values: List[ExtractedValue] = Field(default_factory=list)
    missing_field_ids: List[str] = Field(default_factory=list)
    ambiguous_field_ids: List[str] = Field(default_factory=list)

class NormalizeValueRequest(BaseModel):
    locale: str
    input_type: str
    transcript: str

class NormalizeValueResponse(BaseModel):
    normalized_value: str
    status: str  # "normalized", "ambiguous"
    needs_clarification: bool = False
