package com.hakdaar.app.data.catalog

import android.content.Context
import com.hakdaar.app.core.model.BlockedTargetsConfig
import com.hakdaar.app.core.model.CatalogTask
import com.hakdaar.app.core.model.TaskCatalog
import kotlinx.serialization.json.Json

class TaskCatalogRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var cachedCatalog: TaskCatalog? = null
    private var cachedBlockedTargets: BlockedTargetsConfig? = null

    fun getCatalog(): TaskCatalog {
        cachedCatalog?.let { return it }
        val catalogJson = context.assets.open("task_catalog.json").bufferedReader().use { it.readText() }
        val catalog = json.decodeFromString<TaskCatalog>(catalogJson)
        cachedCatalog = catalog
        return catalog
    }

    fun getTaskById(taskId: String): CatalogTask? {
        return getCatalog().tasks.find { it.id == taskId }
    }

    fun getBlockedTargets(): BlockedTargetsConfig {
        cachedBlockedTargets?.let { return it }
        val blockedJson = context.assets.open("blocked_targets.json").bufferedReader().use { it.readText() }
        val config = json.decodeFromString<BlockedTargetsConfig>(blockedJson)
        cachedBlockedTargets = config
        return config
    }
}
