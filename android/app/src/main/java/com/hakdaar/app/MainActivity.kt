package com.hakdaar.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hakdaar.app.core.model.BubbleState
import com.hakdaar.app.core.model.CatalogField
import com.hakdaar.app.core.model.CatalogTask
import com.hakdaar.app.core.network.HakdaarApiClient
import com.hakdaar.app.data.catalog.TaskCatalogRepository
import com.hakdaar.app.data.preferences.SettingsDataStore
import com.hakdaar.app.domain.session.TaskSessionManager
import com.hakdaar.app.platform.accessibility.HakdaarAccessibilityService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Screen {
    ONBOARDING,
    SETUP,
    HOME,
    CATALOG,
    TASK_PREFLIGHT,
    FIELD_REVIEW,
    SETTINGS,
    COMPLETED
}

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsDataStore
    private lateinit var catalogRepo: TaskCatalogRepository
    private val apiClient = HakdaarApiClient()

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsDataStore(this)
        catalogRepo = TaskCatalogRepository(this)

        setContent {
            HakdaarAppTheme {
                MainAppNav()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainAppNav() {
        var currentScreen by remember { mutableStateOf(Screen.HOME) }
        var selectedTask by remember { mutableStateOf<CatalogTask?>(null) }
        val bubbleState by TaskSessionManager.bubbleState.collectAsState()
        val activeSession by TaskSessionManager.activeSession.collectAsState()
        val preferredLanguage by settingsStore.languageFlow.collectAsState(initial = "hi-IN")
        val isDisclosureAccepted by settingsStore.disclosureAcceptedFlow.collectAsState(initial = false)

        LaunchedEffect(isDisclosureAccepted) {
            if (!isDisclosureAccepted) {
                currentScreen = Screen.ONBOARDING
            }
        }

        LaunchedEffect(bubbleState) {
            if (bubbleState == BubbleState.FIELD_REVIEW) {
                currentScreen = Screen.FIELD_REVIEW
            } else if (bubbleState == BubbleState.COMPLETED) {
                currentScreen = Screen.COMPLETED
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Hakdaar",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    actions = {
                        IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (currentScreen) {
                    Screen.ONBOARDING -> OnboardingScreen(
                        onAccepted = {
                            lifecycleScope.launch {
                                settingsStore.setDisclosureAccepted(true)
                                currentScreen = Screen.SETUP
                            }
                        }
                    )
                    Screen.SETUP -> SetupScreen(
                        onContinue = { currentScreen = Screen.HOME }
                    )
                    Screen.HOME -> HomeScreen(
                        onStartGuided = { currentScreen = Screen.CATALOG },
                        onSayToFill = {
                            val intent = Intent(this@MainActivity, VoiceCaptureActivity::class.java).apply {
                                putExtra("MODE", "SAY_TO_FILL")
                            }
                            startActivity(intent)
                        },
                        onSelectTask = { task ->
                            selectedTask = task
                            currentScreen = Screen.TASK_PREFLIGHT
                        },
                        onOpenSettings = { currentScreen = Screen.SETTINGS },
                        onEndSession = {
                            TaskSessionManager.clearSession()
                            Toast.makeText(this@MainActivity, "Active session and buffers cleared from RAM", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Screen.CATALOG -> CatalogScreen(
                        tasks = catalogRepo.getCatalog().tasks,
                        onTaskSelected = { task ->
                            selectedTask = task
                            currentScreen = Screen.TASK_PREFLIGHT
                        },
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.TASK_PREFLIGHT -> TaskPreflightScreen(
                        task = selectedTask,
                        onStartTask = { task ->
                            TaskSessionManager.startTask(task)
                            // Launch Demo URL in browser
                            val demoUrl = BuildConfig.HAKDAAR_DEMO_BASE_URL + task.target.urlPath
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(demoUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            Toast.makeText(this@MainActivity, "Opening Demo Form. Floating Hakdaar bubble is active.", Toast.LENGTH_LONG).show()
                        },
                        onSpeakDetails = {
                            val intent = Intent(this@MainActivity, VoiceCaptureActivity::class.java)
                            startActivity(intent)
                        },
                        onBack = { currentScreen = Screen.CATALOG }
                    )
                    Screen.FIELD_REVIEW -> FieldReviewScreen(
                        onConfirmAndStartFilling = {
                            TaskSessionManager.updateBubbleState(BubbleState.WAITING_FOR_PAGE)
                            selectedTask?.let { task ->
                                val demoUrl = BuildConfig.HAKDAAR_DEMO_BASE_URL + task.target.urlPath
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(demoUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(intent)
                            }
                            Toast.makeText(this@MainActivity, "Details confirmed! Hakdaar will guide each field.", Toast.LENGTH_LONG).show()
                        },
                        onCancel = {
                            TaskSessionManager.clearSession()
                            currentScreen = Screen.HOME
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.COMPLETED -> CompletedScreen(
                        onReturnHome = {
                            TaskSessionManager.clearSession()
                            currentScreen = Screen.HOME
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun OnboardingScreen(onAccepted: () -> Unit) {
        var agreeAccessibility by remember { mutableStateOf(false) }
        var agreeCloudAI by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Welcome to Hakdaar",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Hakdaar is a voice co-pilot that helps you fill public forms step-by-step. " +
                            "It know the process, asks questions, and fills approved details directly into eligible fields.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Privacy & Safety Guarantees",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• Hakdaar reads accessible field labels and input types only when starting a task.\n" +
                                    "• It NEVER reads or fills passwords, OTPs, PINs, CVVs, captchas or payments.\n" +
                                    "• It NEVER presses final Submit, Apply, Pay, or Sign buttons.\n" +
                                    "• Speech audio and transcripts are sent to Google services for recognition and structured extraction.\n" +
                                    "• Hakdaar's backend does NOT save request bodies, form data, or user profiles.\n" +
                                    "• The target website or app may retain typed information after it is entered.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreeAccessibility, onCheckedChange = { agreeAccessibility = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I understand Hakdaar uses an Accessibility Service to inspect field labels and fill confirmed text.")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreeCloudAI, onCheckedChange = { agreeCloudAI = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I understand spoken audio is sent to Cloud Speech & Gemini for transcription and extraction.")
                }
            }

            Button(
                onClick = onAccepted,
                enabled = agreeAccessibility && agreeCloudAI,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Continue to Setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun SetupScreen(onContinue: () -> Unit) {
        val isA11yEnabled = isAccessibilityServiceEnabled()
        val isMicGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var backendHealth by remember { mutableStateOf("Checking...") }

        LaunchedEffect(Unit) {
            val res = apiClient.checkHealth()
            backendHealth = res.fold(
                onSuccess = { "Connected (${it.service})" },
                onFailure = { "Offline (${it.localizedMessage ?: "Unreachable"})" }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Setup & Permissions",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Configure required services to enable the floating Hakdaar co-pilot.")

                Spacer(modifier = Modifier.height(24.dp))

                // Accessibility Service
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Accessibility Service", fontWeight = FontWeight.Bold)
                            Text(
                                if (isA11yEnabled) "Enabled" else "Not Enabled",
                                color = if (isA11yEnabled) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                        }
                        Button(
                            onClick = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        ) {
                            Text(if (isA11yEnabled) "Settings" else "Enable")
                        }
                    }
                }

                // Microphone
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Microphone Permission", fontWeight = FontWeight.Bold)
                            Text(
                                if (isMicGranted) "Granted" else "Not Granted",
                                color = if (isMicGranted) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                        }
                        Button(
                            onClick = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        ) {
                            Text(if (isMicGranted) "Granted" else "Request")
                        }
                    }
                }

                // Backend Connection
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Backend Connection", fontWeight = FontWeight.Bold)
                            Text(backendHealth)
                        }
                    }
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Go to Home", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun HomeScreen(
        onStartGuided: () -> Unit,
        onSayToFill: () -> Unit,
        onSelectTask: (CatalogTask) -> Unit,
        onOpenSettings: () -> Unit,
        onEndSession: () -> Unit
    ) {
        val bubbleState by TaskSessionManager.bubbleState.collectAsState()
        val activeSession by TaskSessionManager.activeSession.collectAsState()
        val tasks = catalogRepo.getCatalog().tasks

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Co-Pilot Status", fontWeight = FontWeight.Bold)
                        Text("Bubble State: $bubbleState")
                        if (activeSession != null) {
                            Text("Active Session ID: ${activeSession?.sessionId?.take(8)}...")
                            Text("Collected: ${activeSession?.collectedValues?.size} fields")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onEndSession,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Text("End Session & Clear Data")
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onStartGuided,
                        modifier = Modifier.weight(1f).height(64.dp)
                    ) {
                        Text("Start a Guided Task", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onSayToFill,
                        modifier = Modifier.weight(1f).height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Text("Fill This Field", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Text("Demo Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(tasks) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectTask(task) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(task.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(task.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Simulation Form • Accessible HTML", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    @Composable
    private fun CatalogScreen(
        tasks: List<CatalogTask>,
        onTaskSelected: (CatalogTask) -> Unit,
        onBack: () -> Unit
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text("Task Catalogue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(tasks) { task ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onTaskSelected(task) }) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(task.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(task.description)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Required Fields: ${task.fields.joinToString { it.label }}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Note: This is a simulation, not a government portal.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD97706))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskPreflightScreen(
        task: CatalogTask?,
        onStartTask: (CatalogTask) -> Unit,
        onSpeakDetails: () -> Unit,
        onBack: () -> Unit
    ) {
        if (task == null) return

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Document Checklist Guidance", fontWeight = FontWeight.Bold)
                task.documentChecklist.forEach { doc ->
                    Text("• $doc", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Non-Sensitive Details Needed", fontWeight = FontWeight.Bold)
                task.fields.filter { !it.sensitive }.forEach { field ->
                    Text("• ${field.label} (${field.type})")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                    Text(
                        "Demo Notice: This prototype uses bundled accessible HTML demo forms served locally. No government application will be filed.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF92400E)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSpeakDetails,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Speak All Details by Voice")
                }

                Button(
                    onClick = { onStartTask(task) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Open Demo Portal & Start Filling")
                }
            }
        }
    }

    @Composable
    private fun FieldReviewScreen(
        onConfirmAndStartFilling: () -> Unit,
        onCancel: () -> Unit
    ) {
        val session = TaskSessionManager.activeSession.value
        val task = TaskSessionManager.currentTask.value

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Review Spoken Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Confirm all extracted details before filling starts.")

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(task?.fields ?: emptyList()) { field ->
                        val value = session?.collectedValues?.get(field.id) ?: "Not provided"
                        val quote = session?.sourceQuotes?.get(field.id)

                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(field.label, fontWeight = FontWeight.Bold)
                                Text("Value: $value", color = if (value == "Not provided") Color(0xFFDC2626) else MaterialTheme.colorScheme.primary)
                                quote?.let {
                                    Text("Spoken as: \"$it\"", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onConfirmAndStartFilling,
                    modifier = Modifier.weight(2f).height(48.dp)
                ) {
                    Text("Confirm & Start Filling")
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen(onBack: () -> Unit) {
        val currentLang by settingsStore.languageFlow.collectAsState(initial = "hi-IN")
        val isTtsEnabled by settingsStore.ttsEnabledFlow.collectAsState(initial = true)
        val speechRate by settingsStore.speechRateFlow.collectAsState(initial = 1.0f)

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Spoken Language", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentLang == "hi-IN",
                    onClick = { lifecycleScope.launch { settingsStore.setLanguage("hi-IN") } },
                    label = { Text("Hindi (hi-IN)") }
                )
                FilterChip(
                    selected = currentLang == "en-IN",
                    onClick = { lifecycleScope.launch { settingsStore.setLanguage("en-IN") } },
                    label = { Text("English India (en-IN)") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Spoken Instructions (TTS)", fontWeight = FontWeight.Bold)
                Switch(
                    checked = isTtsEnabled,
                    onCheckedChange = { lifecycleScope.launch { settingsStore.setTtsEnabled(it) } }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    TaskSessionManager.clearSession()
                    Toast.makeText(this@MainActivity, "Session cleared", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Current Session & RAM Buffers")
            }
        }
    }

    @Composable
    private fun CompletedScreen(onReturnHome: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = Color(0xFF059669),
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Hakdaar Session Finished", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Hakdaar has cleared its in-memory task details and audio buffer.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                    Text(
                        "Important Note: The target app or browser website may still display values you entered. Clear or close that page yourself if you do not want them left on screen.",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF92400E)
                    )
                }
            }

            Button(
                onClick = onReturnHome,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Return Home")
            }
        }
    }
}

@Composable
fun HakdaarAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1D4ED8),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E3A8A),
            surface = Color(0xFFF8FAFC),
            onSurface = Color(0xFF0F172A)
        ),
        content = content
    )
}
