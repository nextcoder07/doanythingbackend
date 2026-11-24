package com.hakdaar.app

import com.hakdaar.app.core.model.CatalogTarget
import com.hakdaar.app.core.model.CatalogTask
import com.hakdaar.app.core.network.HakdaarApiClient
import com.hakdaar.app.domain.matching.MatchResult
import com.hakdaar.app.domain.matching.TaskMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TaskMatcherTest {

    private val sampleTasks = listOf(
        CatalogTask(
            id = "birth_certificate_demo",
            title = "Birth Certificate Application (Demo)",
            description = "Demo birth certificate",
            localeAliases = listOf("birth certificate", "janam praman patra", "meri beti ka birth certificate"),
            target = CatalogTarget(kind = "https", urlPath = "/demo/birth-certificate")
        ),
        CatalogTask(
            id = "widow_pension_demo",
            title = "Widow Pension Application (Demo)",
            description = "Demo widow pension",
            localeAliases = listOf("widow pension", "pension ke liye apply karna hai", "vidhwa pension"),
            target = CatalogTarget(kind = "https", urlPath = "/demo/widow-pension")
        )
    )

    private val matcher = TaskMatcher(HakdaarApiClient("http://localhost:8080"))

    @Test
    fun testLocalKeywordMatchingBirthCertificate() = runBlocking {
        val result = matcher.matchTask("Meri beti ka birth certificate banwana hai", sampleTasks)
        assertTrue(result is MatchResult.Matched)
        val matched = result as MatchResult.Matched
        assertEquals("birth_certificate_demo", matched.task.id)
    }

    @Test
    fun testLocalKeywordMatchingWidowPension() = runBlocking {
        val result = matcher.matchTask("Vidhwa pension ke liye aavedan", sampleTasks)
        assertTrue(result is MatchResult.Matched)
        val matched = result as MatchResult.Matched
        assertEquals("widow_pension_demo", matched.task.id)
    }

    @Test
    fun testAmbiguousMatching() = runBlocking {
        val result = matcher.matchTask("Birth certificate aur widow pension dono ke baare me batao", sampleTasks)
        assertTrue(result is MatchResult.Ambiguous)
    }
}
