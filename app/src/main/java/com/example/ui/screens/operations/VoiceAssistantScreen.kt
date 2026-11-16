package com.example.ui.screens.operations

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateUtils
import com.example.core.Money
import com.example.data.local.HisabPulseDatabase
import com.example.data.repository.ParsedVoiceAction
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale

@Composable
fun VoiceAssistantScreen(
    onBack: () -> Unit,
    onOpenInvoiceDetail: (Long) -> Unit,
    onOpenScreenRoute: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val history by app.container.engagementRepository.voiceHistory.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val currency = profile?.currencySymbol ?: "₹"

    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var isListening by remember { mutableStateOf(false) }
    var inputCommand by remember { mutableStateOf("") }
    var parsedAction by remember { mutableStateOf<ParsedVoiceAction?>(null) }
    var resultHeadline by remember { mutableStateOf<String?>(null) }
    var resultDetails by remember { mutableStateOf<List<String>>(emptyList()) }

    var draftItemName by remember { mutableStateOf("") }
    var draftQty by remember { mutableStateOf("1") }
    var draftCustomer by remember { mutableStateOf("Walk-in Customer") }
    var expAmount by remember { mutableStateOf("") }
    var expCategory by remember { mutableStateOf("") }
    var expNotes by remember { mutableStateOf("") }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        engine
    }

    val recognizer = remember {
        if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    fun evaluateCommand(raw: String, fromSpeech: Boolean) {
        if (raw.isBlank()) return
        val action = app.container.engagementRepository.parseVoiceCommand(raw)
        parsedAction = action
        resultHeadline = null
        resultDetails = emptyList()

        when (action) {
            is ParsedVoiceAction.CreateInvoiceDraft -> {
                draftItemName = action.itemQuery
                draftQty = Money.formatPlain(action.qty)
                draftCustomer = "Walk-in Customer"
            }
            is ParsedVoiceAction.CreateExpenseDraft -> {
                expAmount = Money.formatPlain(action.amountRupees)
                expCategory = action.category
                expNotes = action.notes
            }
            is ParsedVoiceAction.ShowTodaysSales -> scope.launch {
                val todayInvs = app.container.engagementRepository.getTodayFinalizedInvoices()
                val total = todayInvs.sumOf { it.grandTotalPaise }
                resultHeadline = "Today's Sales: ${Money.formatPaise(total, currency)} (${todayInvs.size} invoices)"
                resultDetails = todayInvs.take(5).map { "${it.invoiceNumber} • ${it.customerName} • ${Money.formatPaise(it.grandTotalPaise, currency)}" }
                app.container.engagementRepository.recordVoiceHistory(raw, "QUERY_TODAY_SALES", resultHeadline!!, fromSpeech, true)
            }
            is ParsedVoiceAction.CheckCashBalance -> scope.launch {
                val accounts = app.container.accountingRepository.accounts.firstOrNull().orEmpty()
                val cash = accounts.firstOrNull { it.code == HisabPulseDatabase.Companion.AccountCodes.CASH }?.currentBalancePaise ?: 0L
                val bank = accounts.filter { it.isBankAccount || it.code == HisabPulseDatabase.Companion.AccountCodes.BANK }.sumOf { it.currentBalancePaise }
                resultHeadline = "Cash in Hand: ${Money.formatPaise(cash, currency)}"
                resultDetails = listOf("Total Bank Balances: ${Money.formatPaise(bank, currency)}")
                app.container.engagementRepository.recordVoiceHistory(raw, "QUERY_CASH", resultHeadline!!, fromSpeech, true)
            }
            is ParsedVoiceAction.ShowLowStock -> scope.launch {
                val low = app.container.inventoryRepository.lowStockProducts.firstOrNull().orEmpty()
                resultHeadline = "${low.size} Low / Out-of-Stock Product(s)"
                resultDetails = low.take(8).map { "${it.name}: ${Money.formatQty(Money.fromPaise(it.currentStockScaled))} ${it.unitName} (min ${Money.formatQty(Money.fromPaise(it.minStockScaled))})" }
                app.container.engagementRepository.recordVoiceHistory(raw, "QUERY_LOW_STOCK", resultHeadline!!, fromSpeech, true)
            }
            is ParsedVoiceAction.ShowPendingPayments -> scope.launch {
                val overdue = app.container.engagementRepository.getOverdueCustomers()
                val total = overdue.sumOf { it.totalPendingPaise }
                resultHeadline = "Pending Dues: ${Money.formatPaise(total, currency)} (${overdue.size} parties)"
                resultDetails = overdue.take(8).map { "${it.customer.name}: ${Money.formatPaise(it.totalPendingPaise, currency)} (${it.oldestOverdueDays}d)" }
                app.container.engagementRepository.recordVoiceHistory(raw, "QUERY_PENDING", resultHeadline!!, fromSpeech, true)
            }
            is ParsedVoiceAction.SearchProduct -> scope.launch {
                val matches = app.container.inventoryRepository.activeProducts.firstOrNull().orEmpty()
                    .filter { it.name.contains(action.query, true) || it.barcode.contains(action.query, true) }
                resultHeadline = "${matches.size} Product Match(es) for '${action.query}'"
                resultDetails = matches.take(8).map { "${it.name}: Stock ${Money.formatQty(Money.fromPaise(it.currentStockScaled))} ${it.unitName} @ ${Money.formatPaise(it.sellingPricePaise, currency)}" }
                app.container.engagementRepository.recordVoiceHistory(raw, "SEARCH_PRODUCT", resultHeadline!!, fromSpeech, true)
            }
            is ParsedVoiceAction.SearchCustomer -> scope.launch {
                val matches = app.container.partyRepository.customers.firstOrNull().orEmpty()
                    .filter { it.name.contains(action.query, true) || it.phone.contains(action.query, true) }
                resultHeadline = "${matches.size} Customer Match(es) for '${action.query}'"
                resultDetails = matches.take(8).map { "${it.name} (${it.phone.ifBlank { "No phone" }}) • Due: ${Money.formatPaise(it.currentBalancePaise, currency)}" }
                app.container.engagementRepository.recordVoiceHistory(raw, "SEARCH_CUSTOMER", resultHeadline!!, fromSpeech, true)
            }
            is ParsedVoiceAction.SearchInvoice -> scope.launch {
                val matches = app.container.billingRepository.allInvoices.firstOrNull().orEmpty()
                    .filter { it.invoiceNumber.contains(action.invoiceQuery, true) }
                if (matches.size == 1) {
                    app.container.engagementRepository.recordVoiceHistory(raw, "OPEN_INVOICE", "Opened ${matches.first().invoiceNumber}", fromSpeech, true)
                    onOpenInvoiceDetail(matches.first().id)
                } else {
                    resultHeadline = "${matches.size} Invoice(s) matching '${action.invoiceQuery}'"
                    resultDetails = matches.take(6).map { "${it.invoiceNumber} • ${it.customerName} • ${Money.formatPaise(it.grandTotalPaise, currency)}" }
                }
            }
            is ParsedVoiceAction.OpenScreen -> {
                val lower = action.screenName.lowercase()
                val key = when {
                    lower.contains("gst") -> "gst_reports"
                    lower.contains("profit") -> "profit_intelligence"
                    lower.contains("health") -> "business_health"
                    lower.contains("closing") -> "daily_closing"
                    else -> "business_reports"
                }
                scope.launch {
                    app.container.engagementRepository.recordVoiceHistory(raw, "NAVIGATE", "Opened $key", fromSpeech, true)
                    onOpenScreenRoute(key)
                }
            }
            is ParsedVoiceAction.Unrecognized -> {
                resultHeadline = "Could not match command. Try 'Add 5 LED bulbs' or 'Show today's sales'."
            }
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                scope.launch { snackbar.showSnackbar("Voice recognizer error ($error). You can type the command below.") }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    inputCommand = spoken
                    evaluateCommand(spoken, fromSpeech = true)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) inputCommand = partial
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            recognizer?.destroy()
            tts.stop()
            tts.shutdown()
        }
    }

    fun startSpeechCapture() {
        if (recognizer == null) {
            scope.launch { snackbar.showSnackbar("Speech recognizer unavailable — type your command below.") }
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure { scope.launch { snackbar.showSnackbar("Unable to start microphone") } }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeechCapture()
        else scope.launch { snackbar.showSnackbar("Microphone permission denied — use command input below.") }
    }

    val quickCommands = listOf(
        "Show today's sales",
        "How much cash is available?",
        "Which products are low in stock?",
        "Show customers with pending payments",
        "Add 500 rupees transport expense",
        "Create a new invoice"
    )

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Voice Business Assistant",
                subtitle = "Offline parser • Confirm before saving",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val summary = app.container.engagementRepository.buildVoiceReadbackSummary()
                            resultHeadline = "Spoken Business Summary"
                            resultDetails = listOf(summary)
                            if (ttsReady) tts.speak(summary, TextToSpeech.QUEUE_FLUSH, null, "hisab_summary")
                        }
                    }, modifier = Modifier.testTag("read_summary_aloud_btn")) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Read business summary aloud")
                    }
                }
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Voice & Command Console", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusPill(
                                label = if (speechAvailable) "Speech Ready" else "Text Mode",
                                containerColor = if (speechAvailable) semantic.successContainer else semantic.warningContainer,
                                contentColor = if (speechAvailable) semantic.success else semantic.warning
                            )
                        }
                        Text(
                            "Requests on-device offline speech recognition when installed (some devices use Android system speech recognition). Financial actions always require manual confirmation before saving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    if (isListening) {
                                        recognizer?.stopListening()
                                        isListening = false
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) startSpeechCapture() else audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(50.dp).testTag("voice_mic_btn")
                            ) {
                                Icon(if (isListening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                                Text(if (isListening) "Stop Listening" else "Speak Command")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val summary = app.container.engagementRepository.buildVoiceReadbackSummary()
                                        resultHeadline = "Business Summary"
                                        resultDetails = listOf(summary)
                                        if (ttsReady) tts.speak(summary, TextToSpeech.QUEUE_FLUSH, null, "hisab_summary")
                                    }
                                },
                                modifier = Modifier.height(50.dp)
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null)
                                Spacer(Modifier.padding(3.dp))
                                Text("Read Aloud")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = inputCommand,
                                onValueChange = { inputCommand = it },
                                label = { Text("Recognized / Typed Command") },
                                placeholder = { Text("e.g. Add 500 rupees transport expense") },
                                modifier = Modifier.weight(1f).testTag("voice_command_input")
                            )
                            IconButton(
                                onClick = { evaluateCommand(inputCommand, fromSpeech = false) },
                                modifier = Modifier.size(48.dp).testTag("voice_parse_btn")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Parse command")
                            }
                        }
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickCommands.forEach { cmd ->
                                AssistChip(
                                    onClick = {
                                        inputCommand = cmd
                                        evaluateCommand(cmd, fromSpeech = false)
                                    },
                                    label = { Text(cmd) }
                                )
                            }
                        }
                    }
                }
            }

            when (parsedAction) {
                is ParsedVoiceAction.CreateExpenseDraft -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Confirm Expense Before Saving", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = expAmount,
                                    onValueChange = { expAmount = it },
                                    label = { Text("Amount ($currency)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(value = expCategory, onValueChange = { expCategory = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = expNotes, onValueChange = { expNotes = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { parsedAction = null }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                app.container.engagementRepository.executeConfirmedVoiceExpense(
                                                    amountRupees = Money.of(expAmount),
                                                    categoryName = expCategory,
                                                    description = expNotes,
                                                    recognizedText = inputCommand
                                                ).onSuccess {
                                                    parsedAction = null
                                                    snackbar.showSnackbar("Expense confirmed & saved")
                                                }.onFailure { snackbar.showSnackbar(it.message ?: "Error saving expense") }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("confirm_voice_expense_btn")
                                    ) { Text("Confirm & Save") }
                                }
                            }
                        }
                    }
                }
                is ParsedVoiceAction.CreateInvoiceDraft -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Confirm Draft Invoice (Never Auto-Finalized)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = draftCustomer, onValueChange = { draftCustomer = it }, label = { Text("Customer Name") }, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = draftItemName, onValueChange = { draftItemName = it }, label = { Text("Product / Item") }, modifier = Modifier.weight(1.5f))
                                    OutlinedTextField(
                                        value = draftQty,
                                        onValueChange = { draftQty = it },
                                        label = { Text("Qty") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(0.7f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { parsedAction = null }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                app.container.engagementRepository.executeConfirmedVoiceDraftInvoice(
                                                    itemQuery = draftItemName,
                                                    quantity = Money.of(draftQty).max(BigDecimal.ONE),
                                                    customerName = draftCustomer,
                                                    recognizedText = inputCommand
                                                ).onSuccess { newDraftId ->
                                                    parsedAction = null
                                                    snackbar.showSnackbar("Draft invoice created")
                                                    onOpenInvoiceDetail(newDraftId)
                                                }.onFailure { snackbar.showSnackbar(it.message ?: "Failed to create draft") }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("confirm_voice_draft_btn")
                                    ) { Text("Create Draft") }
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }

            if (resultHeadline != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(resultHeadline!!, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            resultDetails.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item { Text("Recent Voice Activity (${history.size})", style = MaterialTheme.typography.titleMedium) }
                items(history.take(15), key = { it.id }) { h ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable {
                            inputCommand = h.recognizedText
                            evaluateCommand(h.recognizedText, fromSpeech = false)
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("\"${h.recognizedText}\"", fontWeight = FontWeight.SemiBold)
                            Text("${h.parsedIntent} • ${h.actionSummary}", style = MaterialTheme.typography.bodySmall)
                            Text(DateUtils.formatDateTime(h.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
