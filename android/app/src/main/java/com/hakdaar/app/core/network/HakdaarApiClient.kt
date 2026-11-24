package com.hakdaar.app.core.network

import com.hakdaar.app.BuildConfig
import com.hakdaar.app.core.model.CatalogField
import com.hakdaar.app.core.model.CatalogTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class HealthResult(val status: String, val service: String)

@Serializable
data class TranscribeResult(val transcript: String, val language_code: String, val status: String)

@Serializable
data class TaskCandidateDto(val id: String, val title: String, val description: String)

@Serializable
data class MatchTaskRequestDto(val transcript: String, val candidates: List<TaskCandidateDto>)

@Serializable
data class MatchTaskResultDto(
    val task_id: String? = null,
    val status: String,
    val needs_clarification: Boolean = false,
    val clarification_question: String? = null
)

@Serializable
data class FieldDefinitionDto(
    val id: String,
    val label: String,
    val type: String,
    val required: Boolean = true,
    val format: String? = null
)

@Serializable
data class ExtractFieldsRequestDto(
    val locale: String,
    val transcript: String,
    val fields: List<FieldDefinitionDto>
)

@Serializable
data class ExtractedValueDto(
    val field_id: String,
    val value: String,
    val source_quote: String,
    val status: String = "extracted"
)

@Serializable
data class ExtractFieldsResultDto(
    val values: List<ExtractedValueDto> = emptyList(),
    val missing_field_ids: List<String> = emptyList(),
    val ambiguous_field_ids: List<String> = emptyList()
)

@Serializable
data class NormalizeValueRequestDto(
    val locale: String,
    val input_type: String,
    val transcript: String
)

@Serializable
data class NormalizeValueResultDto(
    val normalized_value: String,
    val status: String,
    val needs_clarification: Boolean = false
)

class HakdaarApiClient(
    private val baseUrl: String = BuildConfig.HAKDAAR_API_BASE_URL
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun checkHealth(): Result<HealthResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/healthz")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Health check failed with HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val result = json.decodeFromString<HealthResult>(body)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transcribe(wavAudioBytes: ByteArray, languageCode: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mediaType = "audio/wav".toMediaType()
            val requestBody = wavAudioBytes.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$baseUrl/v1/transcribe")
                .post(requestBody)
                .header("Content-Type", "audio/wav")
                .header("X-Audio-Language", languageCode)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorDetail = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Transcription failed (HTTP ${response.code}): $errorDetail"))
                }
                val body = response.body?.string() ?: ""
                val result = json.decodeFromString<TranscribeResult>(body)
                Result.success(result.transcript)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun matchTask(transcript: String, tasks: List<CatalogTask>): Result<MatchTaskResultDto> = withContext(Dispatchers.IO) {
        try {
            val candidates = tasks.map { TaskCandidateDto(it.id, it.title, it.description) }
            val payload = MatchTaskRequestDto(transcript = transcript, candidates = candidates)
            val jsonBody = json.encodeToString(payload).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/v1/match-task")
                .post(jsonBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Task matching failed: HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val result = json.decodeFromString<MatchTaskResultDto>(body)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractFields(
        locale: String,
        transcript: String,
        fields: List<CatalogField>
    ): Result<ExtractFieldsResultDto> = withContext(Dispatchers.IO) {
        try {
            // Filter out sensitive or handover-only fields before sending to API
            val safeFields = fields.filter { !it.sensitive && !it.handoverOnly }.map {
                FieldDefinitionDto(
                    id = it.id,
                    label = it.label,
                    type = it.type,
                    required = it.required,
                    format = it.format
                )
            }

            val payload = ExtractFieldsRequestDto(locale, transcript, safeFields)
            val jsonBody = json.encodeToString(payload).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/v1/extract-fields")
                .post(jsonBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Field extraction failed: HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val result = json.decodeFromString<ExtractFieldsResultDto>(body)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun normalizeValue(
        locale: String,
        inputType: String,
        transcript: String
    ): Result<NormalizeValueResultDto> = withContext(Dispatchers.IO) {
        try {
            val payload = NormalizeValueRequestDto(locale, inputType, transcript)
            val jsonBody = json.encodeToString(payload).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/v1/normalize-value")
                .post(jsonBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Value normalization failed: HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val result = json.decodeFromString<NormalizeValueResultDto>(body)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
