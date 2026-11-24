package com.hakdaar.app

import com.hakdaar.app.core.model.CatalogField
import com.hakdaar.app.core.model.CatalogTarget
import com.hakdaar.app.core.model.CatalogTask
import com.hakdaar.app.domain.session.ActiveTaskSession
import com.hakdaar.app.domain.session.TaskSessionManager
import org.junit.Assert.*
import org.junit.Test

class ActiveTaskSessionTest {

    @Test
    fun testSessionLifecycleAndWipe() {
        val dummyTask = CatalogTask(
            id = "birth_certificate_demo",
            title = "Birth Certificate Application (Demo)",
            description = "Demo task",
            target = CatalogTarget(kind = "https", urlPath = "/demo/birth-certificate"),
            fields = listOf(
                CatalogField(id = "child_name", label = "Child's name", type = "TEXT", required = true),
                CatalogField(id = "date_of_birth", label = "Date of birth", type = "DATE_TEXT", required = true)
            )
        )

        val session = ActiveTaskSession(taskId = dummyTask.id)
        assertNotNull("Session must have a unique random UUID", session.sessionId)
        assertTrue(session.sessionId.isNotBlank())

        // Add collected value
        session.markFieldCompleted("child_name", "Aarav Sharma")
        assertEquals("Aarav Sharma", session.collectedValues["child_name"])
        assertTrue(session.isFieldCompleted("child_name"))
        assertFalse(session.isFieldCompleted("date_of_birth"))

        // Skip date_of_birth
        session.markFieldSkipped("date_of_birth")
        assertTrue(session.isFieldSkipped("date_of_birth"))

        // Wipe session
        session.wipe()
        assertTrue("Collected values must be empty after wipe", session.collectedValues.isEmpty())
        assertTrue("Completed field IDs must be empty after wipe", session.completedFieldIds.isEmpty())
        assertTrue("Skipped field IDs must be empty after wipe", session.skippedFieldIds.isEmpty())
        assertNull(session.activeTranscript)
        assertNull(session.currentFieldDescriptor)
    }

    @Test
    fun testAudioBufferWipingWithZeros() {
        val testBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        TaskSessionManager.storeAudioBuffer(testBytes)

        val retrieved = TaskSessionManager.getAudioBuffer()
        assertNotNull(retrieved)

        // Clear audio buffer
        TaskSessionManager.clearAudioBuffer()
        assertNull(TaskSessionManager.getAudioBuffer())

        // Verify array was wiped with fill(0)
        for (b in testBytes) {
            assertEquals("Audio byte array must be zeroed in RAM", 0.toByte(), b)
        }
    }
}
