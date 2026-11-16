package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.BrandingAssetManager
import com.example.core.DateUtils
import com.example.data.local.entities.AppThemeMode
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.TaxConfiguration
import com.example.data.repository.FullBackupSnapshot
import com.example.data.repository.UserProfile
import com.example.data.repository.UserProfileRepository
import com.example.data.worker.BackupScheduleWorker
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.SearchField
import com.example.ui.components.SignaturePadDialog
import com.example.ui.screens.auth.LegalDialogType
import com.example.ui.screens.auth.LegalDisclosuresDialog
import com.example.ui.screens.auth.signOutFromFirebaseAndCredentials
import com.example.ui.screens.settings.studio.rememberDecodedAssetBitmap
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private data class MoreMenuItemSpec(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val category: String
)

private val MORE_MENU_CATALOG = listOf(
    // A. Smart Business Tools
    MoreMenuItemSpec("voice_assistant", "Voice Business Assistant", "Offline command parser, draft bills & readback", Icons.Default.Mic, "A. Smart Business Tools"),
    MoreMenuItemSpec("daily_closing", "Daily Closing & Cash Register", "Expected vs counted cash reconciliation & Z-report", Icons.Default.LockClock, "A. Smart Business Tools"),
    MoreMenuItemSpec("business_switcher", "Multi-Business Profiles", "Switch or create isolated business books", Icons.Default.SwapHoriz, "A. Smart Business Tools"),

    // B. Smart Inventory
    MoreMenuItemSpec("stock_prediction", "Smart Stock Prediction", "7d/30d/90d velocity, days cover & reorder alerts", Icons.Default.TrendingUp, "B. Smart Inventory"),
    MoreMenuItemSpec("batch_expiry", "Batch & Expiry Tracking", "FEFO batches, expired/7d/30d/60d alerts", Icons.Default.EventBusy, "B. Smart Inventory"),
    MoreMenuItemSpec("price_history", "Price History Tracking", "Purchase, selling & MRP change timeline", Icons.Default.History, "B. Smart Inventory"),
    MoreMenuItemSpec("inventory", "Inventory & Warehouses", "Stock valuation, adjustments & transfers", Icons.Default.Inventory, "B. Smart Inventory"),

    // C. Customer Engagement
    MoreMenuItemSpec("payment_reminders", "Payment Reminders", "Overdue dues with EN/HI/GU WhatsApp & SMS drafts", Icons.Default.NotificationsActive, "C. Customer Engagement"),
    MoreMenuItemSpec("loyalty_program", "Loyalty Points & Tiers", "Silver/Gold/Platinum points, earn & redeem", Icons.Default.Loyalty, "C. Customer Engagement"),

    // D. Advanced Billing & Printing
    MoreMenuItemSpec("pos", "Counter POS", "Fast grid billing & barcode checkout", Icons.Default.PointOfSale, "D. Advanced Billing & Printing"),
    MoreMenuItemSpec("estimates", "Estimates & Quotations", "Estimates, quotations & proforma", Icons.Default.Description, "D. Advanced Billing & Printing"),
    MoreMenuItemSpec("purchases", "Purchases", "Supplier purchase bills & returns", Icons.Default.ShoppingBag, "D. Advanced Billing & Printing"),
    MoreMenuItemSpec("suppliers", "Suppliers", "Supplier directory & payables", Icons.Default.Store, "D. Advanced Billing & Printing"),
    MoreMenuItemSpec("templates", "Invoice Design Studio", "9 customizable templates, paper sizes & colors", Icons.Default.Receipt, "D. Advanced Billing & Printing"),
    MoreMenuItemSpec("printer_settings", "Thermal Bluetooth Printer", "58mm/80mm ESC/POS printing & QR setup", Icons.Default.Print, "D. Advanced Billing & Printing"),
    MoreMenuItemSpec("upi_settings", "UPI QR Payments", "Manage UPI IDs & live QR previews", Icons.Default.QrCode2, "D. Advanced Billing & Printing"),

    // E. Business Analytics
    MoreMenuItemSpec("profit_intelligence", "Profit Intelligence", "Product, category, invoice & margin analytics", Icons.Default.AutoAwesome, "E. Business Analytics"),
    MoreMenuItemSpec("business_health", "Business Health Score", "10 weighted financial & stock health metrics", Icons.Default.HealthAndSafety, "E. Business Analytics"),
    MoreMenuItemSpec("gst_reports", "GST & Tax Reports", "CGST/SGST/IGST & purchase tax summary", Icons.Default.Assessment, "E. Business Analytics"),
    MoreMenuItemSpec("business_reports", "Business Reports", "Sales, stock, dues, PDF & CSV exports", Icons.Default.Assessment, "E. Business Analytics"),

    // F. Data Import, Backup & Recovery
    MoreMenuItemSpec("csv_import_export", "CSV / Excel Import & Export", "Guided column mapping, validation & undo", Icons.Default.UploadFile, "F. Data Import, Backup & Recovery"),
    MoreMenuItemSpec("universal_recovery", "Universal Data Recovery Center", "Inspect & recover .bizmgr, .hpbk, .csv, .xlsx, .db, .zip", Icons.Default.BuildCircle, "F. Data Import, Backup & Recovery"),
    MoreMenuItemSpec("backup", "Encrypted Backup & Schedule", ".bizmgr / .hpbk SAF backups & auto-schedule", Icons.Default.Backup, "F. Data Import, Backup & Recovery"),
    MoreMenuItemSpec("restore", "Restore Backup", "Verify & restore snapshot from file", Icons.Default.Restore, "F. Data Import, Backup & Recovery"),

    // G. Business Management
    MoreMenuItemSpec("payments", "Payments", "Customer receipts & supplier payouts", Icons.Default.Payments, "G. Business Management"),
    MoreMenuItemSpec("cashbook", "Cashbook", "Cash-in, cash-out & daily flow", Icons.Default.AccountBalanceWallet, "G. Business Management"),
    MoreMenuItemSpec("expenses", "Expenses", "Rent, salary, utilities & receipts", Icons.Default.AttachMoney, "G. Business Management"),
    MoreMenuItemSpec("accounting", "Accounting & Books", "Journal, Trial Balance, P&L, Balance Sheet", Icons.Default.MenuBook, "G. Business Management"),
    MoreMenuItemSpec("banks", "Bank Accounts", "Manage business banks & UPI IDs", Icons.Default.AccountBalance, "G. Business Management"),
    MoreMenuItemSpec("profile", "Business Profile", "Logo, GSTIN, address & invoice prefix", Icons.Default.Business, "G. Business Management"),

    // H. Security & Privacy
    MoreMenuItemSpec("security", "Security & App Lock", "PIN, Biometric, auto-lock & hide amounts", Icons.Default.Lock, "H. Security & Privacy"),
    MoreMenuItemSpec("legal_privacy", "Legal & Privacy", "Terms of Service, Privacy Policy & Data Security", Icons.Default.Policy, "H. Security & Privacy"),

    // I. Personalization & Settings
    MoreMenuItemSpec("theme", "Theme Selection", "Light, Charcoal Dark & Orange Brand", Icons.Default.Palette, "I. Personalization & Settings"),
    MoreMenuItemSpec("settings", "App Settings", "Industry modules, batch rules & GST slabs", Icons.Default.Settings, "I. Personalization & Settings"),
    MoreMenuItemSpec("help", "Help & Tutorial", "Replay interactive guide & tips", Icons.AutoMirrored.Filled.Help, "I. Personalization & Settings"),
    MoreMenuItemSpec("about", "About HisabPulse", "App info & 100% offline guarantee", Icons.Default.Info, "I. Personalization & Settings")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreMenuScreen(
    onNavigate: (String) -> Unit
) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        if (query.isBlank()) MORE_MENU_CATALOG
        else MORE_MENU_CATALOG.filter {
            it.title.contains(query, true) || it.subtitle.contains(query, true) || it.category.contains(query, true)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.category } }

    Scaffold(topBar = { TopAppBar(title = { Text("More & Control Center") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigate("business_switcher") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(profile?.businessName ?: "My Business", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Tap to switch or add isolated business profile", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Switch business")
                    }
                }
            }

            item {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search tools, analytics, backup, printer...",
                    modifier = Modifier.testTag("more_search_input")
                )
            }

            if (query.isBlank()) {
                item {
                    Text("Pinned Quick Access", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "voice_assistant" to "Voice AI",
                            "pos" to "Counter POS",
                            "daily_closing" to "Daily Close",
                            "profit_intelligence" to "Profits",
                            "universal_recovery" to "Recovery"
                        ).forEach { (key, label) ->
                            AssistChip(onClick = { onNavigate(key) }, label = { Text(label) })
                        }
                    }
                }
            }

            grouped.forEach { (cat, itemsList) ->
                item(key = "header_$cat") {
                    Spacer(modifier = Modifier.height(6.dp))
                    SectionHeader(cat)
                }
                items(itemsList, key = { it.key }) { spec ->
                    MoreMenuRow(spec.title, spec.subtitle, spec.icon, spec.key) { onNavigate(spec.key) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun MoreMenuRow(title: String, subtitle: String, icon: ImageVector, tagKey: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("more_item_$tagKey"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val history by app.container.backupRepository.backupHistory.collectAsStateWithLifecycle(emptyList())
    val detailedHistory by app.container.backupRepository.detailedBackupHistory.collectAsStateWithLifecycle(emptyList())
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)

    var encrypt by remember { mutableStateOf(true) }
    var useBizMgrExt by remember { mutableStateOf(true) }
    var passphrase by remember { mutableStateOf("") }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ext = if (!encrypt) "json" else if (useBizMgrExt) "bizmgr" else "hpbk"
                val fileName = "hisabpulse_backup_${DateUtils.formatIsoDate(System.currentTimeMillis())}.$ext"
                app.container.backupRepository.exportBackupToUri(uri, fileName, encrypt, passphrase)
                    .onSuccess { meta -> snackbar.showSnackbar("Backup saved (${meta.recordCount} records)") }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Backup failed") }
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            scope.launch {
                app.container.businessRepository.updateAppSettings {
                    it.copy(autoBackupFolderUri = treeUri.toString())
                }
                snackbar.showSnackbar("Auto-backup SAF folder configured")
            }
        }
    }

    Scaffold(topBar = { HisabPulseTopBar(title = "Offline Backup & Schedule", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Create Local Encrypted Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Exports all businesses, parties, products, batches, invoices, purchases, payments, expenses, loyalty & double-entry books via Android SAF.")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("AES-256-GCM Encryption")
                            Switch(checked = encrypt, onCheckedChange = { encrypt = it })
                        }
                        if (encrypt) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = useBizMgrExt, onClick = { useBizMgrExt = true }, label = { Text(".bizmgr (Standard)") })
                                FilterChip(selected = !useBizMgrExt, onClick = { useBizMgrExt = false }, label = { Text(".hpbk (Classic)") })
                            }
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text("Backup Passphrase (Optional)") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Button(
                            onClick = {
                                val ext = if (!encrypt) "json" else if (useBizMgrExt) "bizmgr" else "hpbk"
                                safLauncher.launch("hisabpulse_backup_${DateUtils.formatIsoDate(System.currentTimeMillis())}.$ext")
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_saf_backup_btn")
                        ) { Text("Select Folder & Save Backup") }
                    }
                }
            }

            item {
                val curSchedule = settings?.autoBackupSchedule ?: "OFF"
                val folderSet = !settings?.autoBackupFolderUri.isNullOrBlank()
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Scheduled Offline Auto-Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (folderSet) "SAF Folder linked. Encrypted .bizmgr snapshots save automatically offline."
                            else "Pick a local folder to enable automatic daily or weekly encrypted backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (folderSet) "Change Auto-Backup Folder" else "Choose Auto-Backup Folder")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("OFF" to "Off", "DAILY" to "Daily", "WEEKLY" to "Weekly").forEach { (code, lbl) ->
                                FilterChip(
                                    selected = curSchedule == code,
                                    onClick = {
                                        scope.launch {
                                            app.container.businessRepository.updateAppSettings { it.copy(autoBackupSchedule = code) }
                                            BackupScheduleWorker.scheduleOrCancel(context, code)
                                            snackbar.showSnackbar("Auto-backup schedule: $lbl")
                                        }
                                    },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                    }
                }
            }

            item { Text("Backup History (${maxOf(history.size, detailedHistory.size)})", style = MaterialTheme.typography.titleMedium) }
            if (detailedHistory.isNotEmpty()) {
                items(detailedHistory, key = { "bh_${it.id}" }) { m ->
                    Card {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("${m.fileName} (${m.triggerType})", fontWeight = FontWeight.SemiBold)
                            Text("${m.recordCount} records • ${if (m.isEncrypted) "Encrypted .${m.fileExtension}" else "Plain JSON"} • ${DateUtils.formatDateTime(m.createdAt)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                items(history, key = { "bm_${it.id}" }) { m ->
                    Card {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(m.fileName, fontWeight = FontWeight.SemiBold)
                            Text("${m.recordCount} records • ${if (m.isEncrypted) "Encrypted" else "Plain JSON"} • ${DateUtils.formatDateTime(m.createdAt)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var passphrase by remember { mutableStateOf("") }
    var pendingSnapshot by remember { mutableStateOf<FullBackupSnapshot?>(null) }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                app.container.backupRepository.inspectBackupFromUri(uri, passphrase)
                    .onSuccess { snap -> pendingSnapshot = snap }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Unable to read or decrypt backup") }
            }
        }
    }

    Scaffold(topBar = { HisabPulseTopBar(title = "Restore Backup", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Restore from Backup File", style = MaterialTheme.typography.titleMedium)
                    Text("Backup files are checksum-verified and restored inside an atomic database transaction.")
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Backup Passphrase (if set)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { openLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Choose Backup File") }
                }
            }
        }
    }

    if (pendingSnapshot != null) {
        val snap = pendingSnapshot!!
        ConfirmActionDialog(
            title = "Restore Verified Snapshot?",
            message = "Verified schema v${snap.schemaVersion}: ${snap.customers.size} customers, ${snap.products.size} products, ${snap.invoices.size} invoices. Current local data will be replaced.",
            confirmLabel = "Restore Now",
            onConfirm = {
                scope.launch {
                    app.container.backupRepository.restoreFromSnapshot(snap)
                        .onSuccess {
                            pendingSnapshot = null
                            snackbar.showSnackbar("Backup restored successfully")
                        }
                        .onFailure { snackbar.showSnackbar(it.message ?: "Restore failed") }
                }
            },
            onDismiss = { pendingSnapshot = null }
        )
    }
}

@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val sec by app.container.preferences.securityPreferences.collectAsStateWithLifecycle(com.example.data.preferences.SecurityPreferences())
    val auditLogs by app.container.businessRepository.auditLogs.collectAsStateWithLifecycle(emptyList())
    val biometricReady = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var showPinDialog by remember { mutableStateOf(false) }
    var activeLegalDialog by remember { mutableStateOf<LegalDialogType?>(null) }

    Scaffold(topBar = { HisabPulseTopBar(title = "Security & Privacy", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("PIN Lock", style = MaterialTheme.typography.titleMedium)
                                Text("Single-owner offline app lock", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = sec.pinEnabled,
                                onCheckedChange = { wantEnable ->
                                    if (wantEnable) showPinDialog = true
                                    else scope.launch { app.container.preferences.disablePin() }
                                }
                            )
                        }
                        if (sec.pinEnabled) {
                            OutlinedButton(onClick = { showPinDialog = true }) { Text("Change PIN") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric Unlock", style = MaterialTheme.typography.titleMedium)
                                Text(if (biometricReady) "Fingerprint / face unlock supported" else "Enroll biometrics in Android Settings first", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = sec.biometricEnabled,
                                enabled = sec.pinEnabled && biometricReady,
                                onCheckedChange = { scope.launch { app.container.preferences.setBiometricEnabled(it) } }
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Hide Financial Amounts", style = MaterialTheme.typography.titleMedium)
                                Text("Mask dashboard & ledger totals", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = sec.hideAmounts,
                                onCheckedChange = { hide ->
                                    scope.launch {
                                        app.container.preferences.setHideAmounts(hide)
                                        app.container.businessRepository.updateAppSettings { it.copy(hideAmounts = hide) }
                                    }
                                }
                            )
                        }
                        Text("Auto-Lock Timeout", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 1, 5, 15).forEach { mins ->
                                FilterChip(
                                    selected = sec.autoLockMinutes == mins,
                                    onClick = { scope.launch { app.container.preferences.setAutoLockMinutes(mins) } },
                                    label = { Text(if (mins == 0) "Immediate" else "${mins}m") }
                                )
                            }
                        }
                    }
                }
            }
            item {
                val fbUser = remember { runCatching { Firebase.auth.currentUser }.getOrNull() }
                val syncStatus by app.container.cloudSyncRepository.syncStatus.collectAsStateWithLifecycle()
                if (fbUser != null) {
                    Card {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Cloud Account & Offline Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val statusLabel = when {
                                    syncStatus.isSyncing -> "Syncing..."
                                    syncStatus.hasPendingWrites -> "Offline Queue"
                                    syncStatus.lastError != null -> "Offline Cache"
                                    else -> "Synced"
                                }
                                Text(
                                    statusLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (syncStatus.hasPendingWrites) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                "Signed in as: ${fbUser.email?.ifBlank { null } ?: fbUser.displayName ?: fbUser.uid}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Business profile, Inventory (${syncStatus.syncedProductsCount} items), and Invoices (${syncStatus.syncedInvoicesCount} bills) remain accessible offline and sync automatically when connected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (syncStatus.lastError != null) {
                                Text(
                                    "Offline mode active — local changes will sync automatically when network returns.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            app.container.cloudSyncRepository.syncAllLocalToCloudNow()
                                                .onSuccess { snackbar.showSnackbar("Cloud & offline cache synced") }
                                                .onFailure { snackbar.showSnackbar("Saved offline — will sync when online") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Sync Now", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { signOutFromFirebaseAndCredentials(context, scope) },
                                    modifier = Modifier.weight(1f).testTag("sign_out_btn")
                                ) {
                                    Text("Sign Out", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Privacy, Terms & Legal Disclosures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "HisabPulse follows strict zero-knowledge data architecture. Only 4 shop identity fields sync to Firebase Cloud, while all ledger, accounting and billing remain 100% offline on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { activeLegalDialog = LegalDialogType.PRIVACY_POLICY },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Privacy Policy", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { activeLegalDialog = LegalDialogType.TERMS_OF_SERVICE },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Terms of Use", maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = { activeLegalDialog = LegalDialogType.DATA_ARCHITECTURE },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Data Architecture & Local Security")
                        }
                    }
                }
            }
            item { Text("Recent Audit Log (${auditLogs.size})", style = MaterialTheme.typography.titleMedium) }
            items(auditLogs.take(25), key = { it.id }) { log ->
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${log.action} • ${log.entityType}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                        Text(DateUtils.formatDateTime(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    activeLegalDialog?.let { type ->
        LegalDisclosuresDialog(type = type, onDismiss = { activeLegalDialog = null })
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set 4–6 Digit PIN") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("Enter PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pin.length >= 4) {
                            scope.launch {
                                app.container.preferences.setPin(pin)
                                showPinDialog = false
                                snackbar.showSnackbar("PIN lock enabled")
                            }
                        }
                    },
                    enabled = pin.length >= 4
                ) { Text("Save PIN") }
            },
            dismissButton = { OutlinedButton(onClick = { showPinDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ThemeSelectionScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val current by app.container.preferences.themeMode.collectAsStateWithLifecycle(AppThemeMode.LIGHT)

    Scaffold(topBar = { HisabPulseTopBar(title = "Theme Selection", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppThemeMode.entries.forEach { mode ->
                val selected = mode == current
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .clickable { scope.launch { app.container.businessRepository.setTheme(mode) } }
                        .testTag("theme_option_${mode.name}")
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(mode.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (mode) {
                                    AppThemeMode.LIGHT -> "Clean Light surface with vibrant orange accents"
                                    AppThemeMode.DARK -> "Charcoal dark surface for night & counter use"
                                    AppThemeMode.ORANGE -> "Warm orange brand atmosphere throughout"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Active theme", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val existing by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)

    var businessName by remember { mutableStateOf("") }
    var tagline by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("INV-") }
    var signatureLabel by remember { mutableStateOf("Authorized Signatory") }
    var logoUri by remember { mutableStateOf("") }
    var signatureUri by remember { mutableStateOf("") }
    var sealUri by remember { mutableStateOf("") }
    var showSignaturePad by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                BrandingAssetManager.saveImageUriToInternalPng(context, uri, "logo", 512)
                    .onSuccess { saved ->
                        logoUri = saved
                        snackbar.showSnackbar("Shop logo updated")
                    }
                    .onFailure { err ->
                        snackbar.showSnackbar(err.message ?: "Unable to load selected logo")
                    }
            }
        }
    }

    val signaturePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                BrandingAssetManager.saveImageUriToInternalPng(context, uri, "signature", 640)
                    .onSuccess { saved ->
                        signatureUri = saved
                        snackbar.showSnackbar("Signature image updated")
                    }
                    .onFailure { err ->
                        snackbar.showSnackbar(err.message ?: "Unable to load selected signature")
                    }
            }
        }
    }

    val sealPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                BrandingAssetManager.saveImageUriToInternalPng(context, uri, "seal", 512)
                    .onSuccess { saved ->
                        sealUri = saved
                        snackbar.showSnackbar("Shop seal updated")
                    }
                    .onFailure { err ->
                        snackbar.showSnackbar(err.message ?: "Unable to load selected seal")
                    }
            }
        }
    }

    val logoBmp = rememberDecodedAssetBitmap(logoUri, 256)
    val signatureBmp = rememberDecodedAssetBitmap(signatureUri, 360)
    val sealBmp = rememberDecodedAssetBitmap(sealUri, 256)

    LaunchedEffect(existing) {
        if (existing != null && !loaded) {
            val e = existing!!
            businessName = e.businessName
            tagline = e.tagline
            ownerName = e.ownerName
            phone = e.phone
            email = e.email
            website = e.website
            address = e.address
            gstin = e.gstin
            upiId = e.upiId
            prefix = e.invoicePrefix
            signatureLabel = e.signatureLabel
            logoUri = e.logoUri
            signatureUri = e.signatureUri
            sealUri = e.sealUri
            loaded = true
        }
    }

    Scaffold(topBar = { HisabPulseTopBar(title = "Business Profile", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Shop Logo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (logoBmp != null) {
                                    Image(
                                        bitmap = logoBmp.asImageBitmap(),
                                        contentDescription = "Business Logo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(if (logoUri.isBlank()) "Upload Logo" else "Change")
                                }
                                if (logoUri.isNotBlank()) {
                                    OutlinedButton(onClick = { logoUri = "" }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }
            item { OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Business Name") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = tagline, onValueChange = { tagline = it }, label = { Text("Business Tagline / Slogan") }, placeholder = { Text("Your Business Tagline Here") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, label = { Text("Owner Name") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("Website") }, placeholder = { Text("www.yourwebsite.com") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = gstin, onValueChange = { gstin = it.uppercase() }, label = { Text("GSTIN") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = upiId, onValueChange = { upiId = it }, label = { Text("UPI ID for Invoice QR") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = prefix, onValueChange = { prefix = it.uppercase() }, label = { Text("Invoice Prefix") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = signatureLabel, onValueChange = { signatureLabel = it }, label = { Text("Signature Label") }, modifier = Modifier.fillMaxWidth()) }

            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Authorized Signature & Shop Seal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (signatureBmp != null) {
                                Image(
                                    bitmap = signatureBmp.asImageBitmap(),
                                    contentDescription = "Authorized Signature",
                                    modifier = Modifier.fillMaxSize().padding(6.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text("No signature added yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showSignaturePad = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Draw Signature", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { signaturePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Upload Image", maxLines = 1)
                            }
                        }
                        if (signatureUri.isNotBlank()) {
                            OutlinedButton(onClick = { signatureUri = "" }, modifier = Modifier.fillMaxWidth()) {
                                Text("Clear Signature")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sealBmp != null) {
                                    Image(
                                        bitmap = sealBmp.asImageBitmap(),
                                        contentDescription = "Shop Seal",
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { sealPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Text(if (sealUri.isBlank()) "Upload Shop Seal" else "Change Seal")
                                }
                                if (sealUri.isNotBlank()) {
                                    OutlinedButton(onClick = { sealUri = "" }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            val base = existing ?: BusinessProfile(businessName = businessName)
                            val updated = base.copy(
                                businessName = businessName.ifBlank { "My Business" },
                                tagline = tagline,
                                ownerName = ownerName,
                                phone = phone,
                                email = email,
                                website = website,
                                address = address,
                                gstin = gstin,
                                upiId = upiId,
                                invoicePrefix = prefix.ifBlank { "INV-" },
                                signatureLabel = signatureLabel.ifBlank { "Authorized Signatory" },
                                logoUri = logoUri,
                                signatureUri = signatureUri,
                                sealUri = sealUri
                            )
                            app.container.businessRepository.saveBusinessProfile(updated)
                            runCatching {
                                val uid = Firebase.auth.currentUser?.uid
                                if (uid != null && updated.email.isNotBlank() && updated.address.isNotBlank() && updated.phone.isNotBlank()) {
                                    UserProfileRepository(context).saveUserProfile(
                                        UserProfile(
                                            userId = uid,
                                            email = updated.email,
                                            shopName = updated.businessName,
                                            address = updated.address,
                                            phone = updated.phone
                                        )
                                    )
                                }
                            }
                            snackbar.showSnackbar("Business profile updated")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_business_profile_btn")
                ) { Text("Save Profile") }
            }
        }
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSaved = { savedUri ->
                signatureUri = savedUri
                showSignaturePad = false
                scope.launch {
                    snackbar.showSnackbar("Drawn signature ready — tap Save Profile to apply")
                }
            }
        )
    }
}

@Composable
fun InvoiceTemplatesScreen(onBack: () -> Unit) {
    TemplateDesignerScreen(onBack = onBack)
}

@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val settings by app.container.businessRepository.appSettings.collectAsStateWithLifecycle(null)
    val taxConfigs by app.container.businessRepository.taxConfigurations.collectAsStateWithLifecycle(emptyList())
    var showAddTaxDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { HisabPulseTopBar(title = "App Settings & Modules", onBack = onBack) }) { padding ->
        val s = settings ?: return@Scaffold
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionHeader("Billing, Inventory & Batch Rules") }
            item { SettingToggle("Allow Negative Stock", s.allowNegativeStock) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(allowNegativeStock = v) } } } }
            item { SettingToggle("Auto Round-Off Invoice Totals", s.enableRoundOff) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(enableRoundOff = v) } } } }
            item { SettingToggle("Default Tax-Inclusive Pricing", s.defaultTaxInclusive) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(defaultTaxInclusive = v) } } } }
            item { SettingToggle("Warn When Selling Expired Batch", s.warnExpiredBatchSale) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(warnExpiredBatchSale = v) } } } }
            item { SettingToggle("Block Selling Expired Batches", s.blockExpiredBatchSale) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(blockExpiredBatchSale = v) } } } }

            item { Spacer(modifier = Modifier.height(6.dp)); SectionHeader("Industry Modules") }
            item { SettingToggle("Wholesale Bulk Pricing", s.enableWholesaleModule) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(enableWholesaleModule = v) } } } }
            item { SettingToggle("Medical Batch & Expiry", s.enableMedicalModule) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(enableMedicalModule = v) } } } }
            item { SettingToggle("Garments Size & Color", s.enableGarmentsModule) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(enableGarmentsModule = v) } } } }
            item { SettingToggle("Electronics Serial & Warranty", s.enableElectronicsModule) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(enableElectronicsModule = v) } } } }
            item { SettingToggle("Restaurant Tables & KOT", s.enableRestaurantModule) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(enableRestaurantModule = v) } } } }
            item { SettingToggle("Manufacturing Production", s.enableManufacturingModule) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(enableManufacturingModule = v) } } } }
            item { SettingToggle("Service Business Items", s.enableServiceModule) { v -> scope.launch { app.container.businessRepository.updateAppSettings { it.copy(enableServiceModule = v) } } } }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("GST Tax Slabs (${taxConfigs.size})")
                    OutlinedButton(onClick = { showAddTaxDialog = true }) { Text("Add Slab") }
                }
            }
            items(taxConfigs, key = { it.id }) { slab ->
                Card {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(slab.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "CGST ${slab.cgstPercentScaled / 100.0}% • SGST ${slab.sgstPercentScaled / 100.0}% • IGST ${slab.igstPercentScaled / 100.0}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!slab.isDefault) {
                            IconButton(onClick = { scope.launch { app.container.businessRepository.deleteTaxConfiguration(slab) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete GST slab")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(6.dp)); SectionHeader("Language") }
            item {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "hi" to "Hindi", "mr" to "Marathi", "gu" to "Gujarati").forEach { (code, name) ->
                        FilterChip(
                            selected = s.languageCode == code,
                            onClick = { scope.launch { app.container.businessRepository.updateAppSettings { it.copy(languageCode = code) } } },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }
    }

    if (showAddTaxDialog) {
        var name by remember { mutableStateOf("") }
        var rate by remember { mutableStateOf("18") }
        AlertDialog(
            onDismissRequest = { showAddTaxDialog = false },
            title = { Text("Add Custom GST Slab") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Slab Name (e.g. GST 3%)") })
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Total GST %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pct = rate.toDoubleOrNull() ?: 0.0
                    val scaled = kotlin.math.round(pct * 100.0).toLong().coerceAtLeast(0L)
                    val half = scaled / 2L
                    scope.launch {
                        app.container.businessRepository.saveTaxConfiguration(
                            TaxConfiguration(
                                name = name.ifBlank { "GST ${pct}%" },
                                ratePercentScaled = scaled,
                                cgstPercentScaled = half,
                                sgstPercentScaled = scaled - half,
                                igstPercentScaled = scaled
                            )
                        )
                        showAddTaxDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { OutlinedButton(onClick = { showAddTaxDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit, onReplayTutorial: () -> Unit) {
    Scaffold(topBar = { HisabPulseTopBar(title = "Help & Guide", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Interactive Feature Walkthrough", style = MaterialTheme.typography.titleMedium)
                    Text("Replay the step-by-step guide covering Dashboard, Billing, Draft Editing, POS, Inventory, Khata, and Reports.")
                    Button(onClick = onReplayTutorial) { Text("Start Interactive Tutorial") }
                }
            }
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Accounting & Draft Rules", style = MaterialTheme.typography.titleMedium)
                    Text("• Draft bills do not affect stock or customer balances until finalized.")
                    Text("• Finalized bills are immutable; use Sales Return or Cancel & Reverse for auditable corrections.")
                    Text("• Every sale, purchase, payment, and expense posts a balanced double-entry journal.")
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    var activeLegalDialog by remember { mutableStateOf<LegalDialogType?>(null) }
    Scaffold(topBar = { HisabPulseTopBar(title = "About HisabPulse", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HisabPulse v1.0", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Offline-First Business Management, GST Billing, POS, Inventory & Double-Entry Accounting Suite.")
                    Text("Designed for a single business owner operating offline with BigDecimal precision, local Room SQLite persistence, and AES-256-GCM encrypted Storage Access Framework backups.")
                }
            }
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Legal, Privacy & Compliance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Review our official policies governing your business data, cloud synchronization, and offline security.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { activeLegalDialog = LegalDialogType.PRIVACY_POLICY },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Privacy Policy (Data Minimization)")
                    }
                    OutlinedButton(
                        onClick = { activeLegalDialog = LegalDialogType.TERMS_OF_SERVICE },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Terms & Conditions")
                    }
                    OutlinedButton(
                        onClick = { activeLegalDialog = LegalDialogType.DATA_ARCHITECTURE },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Data Security & Local-First Architecture")
                    }
                }
            }
        }
    }
    activeLegalDialog?.let { type ->
        LegalDisclosuresDialog(type = type, onDismiss = { activeLegalDialog = null })
    }
}
