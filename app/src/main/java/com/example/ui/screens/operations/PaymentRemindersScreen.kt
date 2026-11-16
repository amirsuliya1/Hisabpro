package com.example.ui.screens.operations

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.DateUtils
import com.example.core.Money
import com.example.data.local.entities.ReminderChannel
import com.example.data.repository.OverdueCustomerItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSemanticColors
import kotlinx.coroutines.launch

@Composable
fun PaymentRemindersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val semantic = LocalSemanticColors.current

    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val history by app.container.engagementRepository.reminderHistory.collectAsStateWithLifecycle(emptyList())
    val currency = profile?.currencySymbol ?: "₹"

    var overdueList by remember { mutableStateOf<List<OverdueCustomerItem>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var lang by remember { mutableStateOf("en") }
    var activeItem by remember { mutableStateOf<OverdueCustomerItem?>(null) }
    var editableMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        overdueList = app.container.engagementRepository.getOverdueCustomers()
    }

    LaunchedEffect(activeItem, lang) {
        val item = activeItem ?: return@LaunchedEffect
        editableMsg = app.container.engagementRepository.buildReminderMessage(
            customerName = item.customer.name,
            amountDuePaise = item.totalPendingPaise,
            invoiceNumber = item.unpaidInvoices.firstOrNull()?.invoiceNumber.orEmpty(),
            languageCode = lang,
            upiId = profile?.upiId.orEmpty()
        )
    }

    fun dispatchReminder(item: OverdueCustomerItem, channel: ReminderChannel, msg: String) {
        scope.launch {
            val cleanPhone = item.customer.phone.replace(Regex("[^0-9+]"), "")
            when (channel) {
                ReminderChannel.WHATSAPP -> {
                    val waUri = Uri.parse("https://wa.me/${cleanPhone.removePrefix("+")}?text=${Uri.encode(msg)}")
                    val intent = Intent(Intent.ACTION_VIEW, waUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, msg)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(Intent.createChooser(share, "Share Reminder").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                }
                ReminderChannel.SMS -> {
                    val smsUri = Uri.parse("smsto:$cleanPhone")
                    val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                        putExtra("sms_body", msg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure { snackbar.showSnackbar("No SMS app available") }
                }
                ReminderChannel.COPY -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Payment Reminder", msg))
                }
                ReminderChannel.MANUAL -> Unit
            }
            app.container.engagementRepository.logReminderAttempt(
                customerId = item.customer.id,
                customerName = item.customer.name,
                invoiceId = item.unpaidInvoices.firstOrNull()?.id,
                invoiceNumber = item.unpaidInvoices.firstOrNull()?.invoiceNumber.orEmpty(),
                amountDuePaise = item.totalPendingPaise,
                channel = channel,
                languageCode = lang,
                messageText = msg
            )
            activeItem = null
            snackbar.showSnackbar("Reminder logged (${channel.label})")
        }
    }

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Payment Reminders",
                subtitle = "Overdue dues & localized WhatsApp/SMS templates",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overdue (${overdueList.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Reminder History (${history.size})") })
            }

            if (selectedTab == 0) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "hi" to "Hindi", "gu" to "Gujarati").forEach { (code, name) ->
                        FilterChip(selected = lang == code, onClick = { lang = code }, label = { Text(name) })
                    }
                }
                if (overdueList.isEmpty()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EmptyStateCard(
                            title = "All Customer Dues Cleared",
                            description = "Customers with positive receivable balances will appear here with overdue aging and one-tap reminders."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(overdueList, key = { it.customer.id }) { item ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${item.customer.phone.ifBlank { "No phone" }} • ${item.unpaidInvoices.size} unpaid bill(s)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(Money.formatPaise(item.totalPendingPaise, currency), fontWeight = FontWeight.Bold, color = semantic.danger)
                                            StatusPill(
                                                label = if (item.oldestOverdueDays > 0) "${item.oldestOverdueDays}d overdue" else "Due now",
                                                containerColor = semantic.warningContainer,
                                                contentColor = semantic.warning
                                            )
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Button(
                                            onClick = { activeItem = item },
                                            modifier = Modifier.testTag("compose_reminder_btn_${item.customer.id}")
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                            Spacer(Modifier.padding(3.dp))
                                            Text("Compose & Send")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (history.isEmpty()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EmptyStateCard(
                            title = "No Reminders Logged Yet",
                            description = "When you compose or copy payment reminders, an audit entry with its honest dispatch status is logged here."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { it.id }) { h ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(h.customerName, fontWeight = FontWeight.Bold)
                                        StatusPill(h.channel.label, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                    Text("Due: ${Money.formatPaise(h.amountDuePaise, currency)} • ${h.languageCode.uppercase()}", style = MaterialTheme.typography.bodySmall)
                                    Text(h.messageText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${h.deliveryNote} • ${DateUtils.formatDateTime(h.sentAtMillis)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeItem != null) {
        val target = activeItem!!
        AlertDialog(
            onDismissRequest = { activeItem = null },
            title = { Text("Send Reminder • ${target.customer.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("en" to "EN", "hi" to "HI", "gu" to "GU").forEach { (code, lbl) ->
                            FilterChip(selected = lang == code, onClick = { lang = code }, label = { Text(lbl) })
                        }
                    }
                    OutlinedTextField(
                        value = editableMsg,
                        onValueChange = { editableMsg = it },
                        label = { Text("Message Preview") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Note: Opening WhatsApp or SMS hands off to the external messaging app; actual delivery happens inside that app.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { dispatchReminder(target, ReminderChannel.WHATSAPP, editableMsg) }, modifier = Modifier.weight(1f)) {
                            Text("WhatsApp")
                        }
                        OutlinedButton(onClick = { dispatchReminder(target, ReminderChannel.SMS, editableMsg) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Sms, contentDescription = null)
                            Spacer(Modifier.padding(2.dp))
                            Text("SMS")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { dispatchReminder(target, ReminderChannel.COPY, editableMsg) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.padding(2.dp))
                            Text("Copy")
                        }
                        Button(onClick = { dispatchReminder(target, ReminderChannel.MANUAL, editableMsg) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.padding(2.dp))
                            Text("Mark Sent")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { activeItem = null }) { Text("Close") } }
        )
    }
}
