package com.hakdaar.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskCatalog(
    val catalogVersion: Int = 1,
    val tasks: List<CatalogTask> = emptyList()
)

@Serializable
data class CatalogTask(
    val id: String,
    val title: String,
    val description: String,
    val localeAliases: List<String> = emptyList(),
    val target: CatalogTarget,
    val documentChecklist: List<String> = emptyList(),
    val fields: List<CatalogField> = emptyList(),
    val pages: List<CatalogPage> = emptyList()
)

@Serializable
data class CatalogTarget(
    val kind: String, // "https" or "android_package"
    val urlPath: String,
    val expectedHostMode: String = "hakdaar_demo_host",
    val targetPackage: String? = null
)

@Serializable
data class CatalogField(
    val id: String,
    val label: String,
    val type: String, // "TEXT", "DATE_TEXT", "EMAIL", "PHONE", "NUMBER"
    val required: Boolean = true,
    val sensitive: Boolean = false,
    val handoverOnly: Boolean = false,
    val format: String? = null
)

@Serializable
data class CatalogPage(
    val id: String,
    val pageSignature: PageSignature,
    val fieldsInOrder: List<String> = emptyList(),
    val selectors: Map<String, FieldSelector> = emptyMap(),
    val navigationInstruction: String? = null
)

@Serializable
data class PageSignature(
    val headingExact: String,
    val requiredVisibleLabels: List<String> = emptyList()
)

@Serializable
data class FieldSelector(
    val labelExact: String,
    val inputType: String
)
