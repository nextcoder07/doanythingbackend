package com.hakdaar.app

import com.hakdaar.app.core.model.BlockedTargetsConfig
import com.hakdaar.app.core.model.TaskCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TaskCatalogValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testTaskCatalogJsonValidAndComplete() {
        val catalogFile = File("src/main/assets/task_catalog.json")
        assertTrue("task_catalog.json must exist in assets", catalogFile.exists())

        val catalogJson = catalogFile.readText()
        val catalog = json.decodeFromString<TaskCatalog>(catalogJson)

        assertEquals("Catalog version must be 1", 1, catalog.catalogVersion)
        assertEquals("Catalog must contain exactly 2 seeded demo tasks", 2, catalog.tasks.size)

        // Verify Birth Certificate Demo Task
        val birthTask = catalog.tasks.find { it.id == "birth_certificate_demo" }
        assertNotNull("Birth Certificate task must exist", birthTask)
        assertEquals("Birth Certificate Application (Demo)", birthTask?.title)
        assertTrue(birthTask?.fields?.any { it.id == "child_name" } == true)
        assertTrue(birthTask?.fields?.any { it.id == "date_of_birth" } == true)
        assertTrue(birthTask?.fields?.any { it.id == "place_of_birth" } == true)
        assertTrue(birthTask?.fields?.any { it.id == "father_name" } == true)
        assertTrue(birthTask?.fields?.any { it.id == "mother_name" } == true)

        // Verify Widow Pension Demo Task
        val pensionTask = catalog.tasks.find { it.id == "widow_pension_demo" }
        assertNotNull("Widow Pension task must exist", pensionTask)
        assertEquals("Widow Pension Application (Demo)", pensionTask?.title)
        assertTrue(pensionTask?.fields?.any { it.id == "applicant_name" } == true)
        assertTrue(pensionTask?.fields?.any { it.id == "spouse_name" } == true)
        assertTrue(pensionTask?.fields?.any { it.id == "date_of_death" } == true)
        assertTrue(pensionTask?.fields?.any { it.id == "district" } == true)
        assertTrue(pensionTask?.fields?.any { it.id == "address" } == true)
    }

    @Test
    fun testBlockedTargetsJsonValidAndComplete() {
        val blockedFile = File("src/main/assets/blocked_targets.json")
        assertTrue("blocked_targets.json must exist in assets", blockedFile.exists())

        val blockedJson = blockedFile.readText()
        val config = json.decodeFromString<BlockedTargetsConfig>(blockedJson)

        assertTrue("Must have blocked banking/payment packages", config.blockedPackages.isNotEmpty())
        assertTrue("Must include phonepe", config.blockedPackages.contains("com.phonepe.app"))
        assertTrue("Must include paytm", config.blockedPackages.contains("net.one97.paytm"))

        assertTrue("Must have blocked banking domains", config.blockedDomains.isNotEmpty())
        assertTrue("Must include sbi", config.blockedDomains.contains("onlinesbi.sbi"))

        assertTrue("Must have English sensitive terms", config.englishSensitiveTerms.contains("password"))
        assertTrue("Must have English OTP", config.englishSensitiveTerms.contains("otp"))
        assertTrue("Must have English UPI PIN", config.englishSensitiveTerms.contains("upi pin"))

        assertTrue("Must have Hindi sensitive terms", config.hindiSensitiveTerms.contains("पासवर्ड"))
        assertTrue("Must have Hindi OTP", config.hindiSensitiveTerms.contains("ओटीपी"))
    }
}
