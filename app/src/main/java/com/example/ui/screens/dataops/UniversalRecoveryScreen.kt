package com.example.ui.screens.dataops

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateUtils
import com.example.core.FileInspectionReport
import com.example.data.local.entities.DuplicateStrategy
import com.example.data.local.entities.RecoveryMode
import com.example.data.repository.UniversalRecoveryResult
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.launch

@Composable
fun UniversalRecoveryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val history by app.container.recoveryRepository.recoveryHistory.collectAsStateWithLifecycle(emptyList())

    var selectedFileName by remember { mutableStateOf("") }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var report by remember { mutableStateOf<FileInspectionReport?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(RecoveryMode.MERGE_IMPORT) }
    var duplicateStrategy by remember { mutableStateOf(DuplicateStrategy.SKIP) }
    var selectedCategories by remember { mutableStateOf(setOf("PRODUCTS", "CUSTOMERS", "SUPPLIERS", "INVOICES", "EXPENSES")) }
    var newBusinessName by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<UniversalRecoveryResult?>(null) }

    fun queryName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "unknown_file"
        }
        return uri.lastPathSegment ?: "unknown_file"
    }

    val openAnyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val fName = queryName(uri)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    snackbar.showSnackbar("Unable to open selected file")
                    return@launch
                }
                selectedFileName = fName
                fileBytes = bytes
                report = app.container.recoveryRepository.inspectRecoveryFile(fName, bytes)
                lastResult = null
            }
        }
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Universal Data Recovery Center",
                subtitle = "Magic-byte inspector for .bizmgr, .hpbk, .json, .csv, .xlsx, .db, .zip & .vyp",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Inspect Any Backup or Business File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Inspects true file signatures (not just extensions), protects against zip-slip/zip-bombs, creates a pre-recovery safety backup automatically, and never guesses proprietary encrypted formats.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { openAnyLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pick_recovery_file_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(if (selectedFileName.isBlank()) "Choose Any File to Inspect" else "Selected: $selectedFileName")
                        }
                    }
                }
            }

            val rep = report
            if (rep != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Diagnostic File Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                StatusPill(
                                    label = if (rep.canDirectlyRecover) "Recoverable" else "Honest Notice",
                                    containerColor = if (rep.canDirectlyRecover) semantic.successContainer else semantic.warningContainer,
                                    contentColor = if (rep.canDirectlyRecover) semantic.success else semantic.warning
                                )
                            }
                            Text("Format: ${rep.detectedFormat.label}", fontWeight = FontWeight.SemiBold)
                            Text("Size: ${rep.sizeBytes} bytes • SHA-256: ${rep.sha256.take(16)}...", style = MaterialTheme.typography.labelSmall)
                            Text(rep.summaryMessage, style = MaterialTheme.typography.bodyMedium)
                            if (rep.honestLimitationNote.isNotBlank()) {
                                Text(rep.honestLimitationNote, style = MaterialTheme.typography.bodySmall, color = semantic.warning)
                            }
                            if (rep.detectedEntries.isNotEmpty()) {
                                Text("Archive entries (${rep.detectedEntries.size}): ${rep.detectedEntries.take(8).joinToString()}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (rep.canDirectlyRecover) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Configure Recovery Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RecoveryMode.entries.forEach { m ->
                                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                                    }
                                }
                                if (rep.isEncrypted) {
                                    OutlinedTextField(
                                        value = passphrase,
                                        onValueChange = { passphrase = it },
                                        label = { Text("Backup Passphrase (if set)") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (mode == RecoveryMode.NEW_BUSINESS_IMPORT) {
                                    OutlinedTextField(
                                        value = newBusinessName,
                                        onValueChange = { newBusinessName = it },
                                        label = { Text("New Business Profile Name") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (mode == RecoveryMode.SELECTIVE_RECOVERY || mode == RecoveryMode.MERGE_IMPORT || mode == RecoveryMode.NEW_BUSINESS_IMPORT) {
                                    Text("Data Categories", style = MaterialTheme.typography.labelLarge)
                                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf("PRODUCTS", "CUSTOMERS", "SUPPLIERS", "INVOICES", "EXPENSES").forEach { cat ->
                                            val checked = cat in selectedCategories
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = {
                                                        selectedCategories = if (it) selectedCategories + cat else selectedCategories - cat
                                                    }
                                                )
                                                Text(cat.lowercase().replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(DuplicateStrategy.SKIP, DuplicateStrategy.UPDATE).forEach { s ->
                                        FilterChip(selected = duplicateStrategy == s, onClick = { duplicateStrategy = s }, label = { Text(s.label) })
                                    }
                                }
                                Button(
                                    onClick = {
                                        val bytes = fileBytes ?: return@Button
                                        scope.launch {
                                            app.container.recoveryRepository.executeUniversalRecovery(
                                                fileName = selectedFileName,
                                                bytes = bytes,
                                                passphrase = passphrase,
                                                mode = mode,
                                                duplicateStrategy = duplicateStrategy,
                                                selectedCategories = selectedCategories,
                                                newBusinessName = newBusinessName
                                            ).onSuccess { res ->
                                                lastResult = res
                                                snackbar.showSnackbar("Recovery ${res.status}: ${res.recoveredCount} records")
                                            }.onFailure { snackbar.showSnackbar(it.message ?: "Recovery failed") }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("execute_recovery_btn")
                                ) {
                                    Icon(Icons.Default.BuildCircle, contentDescription = null)
                                    Spacer(Modifier.padding(4.dp))
                                    Text("Run Recovery (${mode.label})")
                                }
                            }
                        }
                    }
                }
            }

            val res = lastResult
            if (res != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = semantic.successContainer.copy(alpha = 0.45f))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Recovery Result • ${res.status}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(res.summary)
                            Text("Safety backup created: ${res.safetyBackupFile}", style = MaterialTheme.typography.labelSmall)
                            res.records.take(10).forEach { rec ->
                                Text("• [${rec.status}] ${rec.recordCategory}: ${rec.sourceIdentifier} ${rec.message}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item { Text("Recovery Audit History (${history.size})", style = MaterialTheme.typography.titleMedium) }
            items(history, key = { it.id }) { h ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(h.sourceFileName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(h.status, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text("${h.detectedFileType} • ${h.recoveryMode.label}", style = MaterialTheme.typography.bodySmall)
                        Text(h.reportSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Recovered: ${h.recoveredCount} • Unsupported: ${h.unsupportedCount} • Errors: ${h.errorCount} • ${DateUtils.formatDateTime(h.createdAt)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
