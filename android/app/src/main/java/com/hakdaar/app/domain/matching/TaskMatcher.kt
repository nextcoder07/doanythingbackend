package com.hakdaar.app.domain.matching

import com.hakdaar.app.core.model.CatalogTask
import com.hakdaar.app.core.network.HakdaarApiClient

sealed class MatchResult {
    data class Matched(val task: CatalogTask) : MatchResult()
    data class Ambiguous(val candidateTasks: List<CatalogTask>, val question: String?) : MatchResult()
    object NoMatch : MatchResult()
}

class TaskMatcher(
    private val apiClient: HakdaarApiClient
) {
    suspend fun matchTask(
        transcript: String,
        allTasks: List<CatalogTask>
    ): MatchResult {
        val clean = transcript.trim().lowercase()

        // 1. Local keyword / alias matching first
        val localMatches = allTasks.filter { task ->
            task.localeAliases.any { alias -> clean.contains(alias.lowercase()) } ||
                    clean.contains(task.title.lowercase()) ||
                    (clean.contains("birth") && task.id == "birth_certificate_demo") ||
                    (clean.contains("janam") && task.id == "birth_certificate_demo") ||
                    (clean.contains("pension") && task.id == "widow_pension_demo") ||
                    (clean.contains("widow") && task.id == "widow_pension_demo") ||
                    (clean.contains("vidhwa") && task.id == "widow_pension_demo")
        }

        if (localMatches.size == 1) {
            return MatchResult.Matched(localMatches.first())
        }

        if (localMatches.size > 1) {
            return MatchResult.Ambiguous(
                candidateTasks = localMatches,
                question = "Which application would you like to start?"
            )
        }

        // 2. Call backend match endpoint with candidates
        val backendResult = apiClient.matchTask(transcript, allTasks)
        return backendResult.fold(
            onSuccess = { dto ->
                when (dto.status) {
                    "matched" -> {
                        val matchedTask = allTasks.find { it.id == dto.task_id }
                        if (matchedTask != null) {
                            MatchResult.Matched(matchedTask)
                        } else {
                            MatchResult.NoMatch
                        }
                    }
                    "ambiguous" -> {
                        MatchResult.Ambiguous(
                            candidateTasks = allTasks,
                            question = dto.clarification_question ?: "Which application would you like to start?"
                        )
                    }
                    else -> MatchResult.NoMatch
                }
            },
            onFailure = {
                MatchResult.NoMatch
            }
        )
    }
}
