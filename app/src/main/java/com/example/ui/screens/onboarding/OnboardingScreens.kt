package com.example.ui.screens.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.HisabPulseApp
import com.example.R
import com.example.core.DateUtils
import com.example.core.GstCalculator
import com.example.data.local.entities.AppThemeMode
import com.example.data.local.entities.BusinessCategory
import com.example.data.local.entities.BusinessProfile
import com.example.ui.components.HISAB_TUTORIAL_STEPS
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.StatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onNavigateToWelcome: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scale = remember { Animatable(0.82f) }

    LaunchedEffect(Unit) {
        val onboarded = app.container.preferences.isOnboardingCompletedSync() ||
            app.container.preferences.onboardingCompleted.first() ||
            (app.container.businessRepository.getBusinessProfileOnce()?.businessName?.isNotBlank() == true)
        if (onboarded) {
            app.container.preferences.setOnboardingCompleted(true)
            onNavigateToDashboard()
        } else {
            scale.animateTo(1f, animationSpec = tween(320))
            delay(350)
            onNavigateToWelcome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_art),
                contentDescription = "HisabPulse Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "HisabPulse",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Offline Business, GST Billing & Accounting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    onStartSetup: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Image(
                    painter = painterResource(id = R.drawable.img_welcome_hero),
                    contentDescription = "HisabPulse business management",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(20.dp))
                StatusPill(
                    label = "100% OFFLINE • SINGLE-OWNER SUITE",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Run Your Entire Shop From One Phone",
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "HisabPulse brings GST billing, Khata ledgers, inventory, fast POS, cashbook, double-entry accounting, and encrypted local backups together without requiring internet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))
                FeatureHighlightRow(Icons.Default.ReceiptLong, "GST & Non-GST Billing", "Invoices, estimates, returns, draft editing & PDF sharing")
                FeatureHighlightRow(Icons.Default.PointOfSale, "Counter POS & Barcode", "CameraX barcode scanning, hold bills & split payments")
                FeatureHighlightRow(Icons.Default.AccountBalanceWallet, "Double-Entry Khata & Books", "Real-time Trial Balance, P&L, Balance Sheet & Cashbook")
                FeatureHighlightRow(Icons.Default.Security, "Private Encrypted Backups", "PIN & Biometric lock with AES-256 SAF local backup")
            }

            Column(modifier = Modifier.padding(top = 20.dp)) {
                Button(
                    onClick = onStartSetup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("welcome_get_started")
                ) {
                    Text("Set Up My Business", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessSetupScreen(
    currentTheme: AppThemeMode,
    onThemeChanged: (AppThemeMode) -> Unit,
    onSetupCompleted: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) } // Steps 1..7
    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var logoUri by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(BusinessCategory.RETAIL) }

    var enableWholesale by remember { mutableStateOf(false) }
    var enableMedical by remember { mutableStateOf(false) }
    var enableGarments by remember { mutableStateOf(false) }
    var enableElectronics by remember { mutableStateOf(false) }
    var enableRestaurant by remember { mutableStateOf(false) }
    var enableManufacturing by remember { mutableStateOf(false) }
    var enableService by remember { mutableStateOf(false) }

    var isGstRegistered by remember { mutableStateOf(true) }
    var gstin by remember { mutableStateOf("") }
    var financialYear by remember { mutableStateOf(DateUtils.currentFinancialYearLabel()) }
    var currencySymbol by remember { mutableStateOf("₹") }

    var invoicePrefix by remember { mutableStateOf("INV-") }
    var nextInvoiceNumber by remember { mutableStateOf("1001") }
    var terms by remember { mutableStateOf("Thank you for your business!") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) logoUri = uri.toString()
    }

    val stepTitles = listOf(
        "Welcome Overview",
        "Business Information",
        "Business Type & Modules",
        "Tax & GST Configuration",
        "Invoice Configuration",
        "Theme Selection",
        "Setup Complete"
    )

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Business Setup (${step}/7)",
                subtitle = stepTitles[step - 1],
                onBack = if (step > 1) { { step-- } } else null,
                actions = {
                    if (step in 2..5) {
                        TextButton(onClick = { step++ }) { Text("Skip") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            LinearProgressIndicator(
                progress = { step / 7f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedContent(targetState = step, label = "onboarding_step") { currentStep ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (currentStep) {
                            1 -> {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Welcome to HisabPulse", style = MaterialTheme.typography.headlineMedium)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Let's personalize your invoices, GST tax rules, industry tools, and color theme in under a minute. Optional fields can be skipped and updated anytime in Settings.",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                            2 -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (logoUri.isNotBlank()) {
                                            AsyncImage(model = logoUri, contentDescription = "Business logo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick logo")
                                        }
                                    }
                                    Column {
                                        Text("Business Logo (Optional)", style = MaterialTheme.typography.titleMedium)
                                        TextButton(onClick = {
                                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }) { Text("Choose from Gallery") }
                                    }
                                }
                                OutlinedTextField(
                                    value = businessName,
                                    onValueChange = { businessName = it; errorMsg = null },
                                    label = { Text("Business / Shop Name *") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("setup_business_name")
                                )
                                OutlinedTextField(
                                    value = ownerName,
                                    onValueChange = { ownerName = it },
                                    label = { Text("Owner Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = phone,
                                        onValueChange = { phone = it },
                                        label = { Text("Phone") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text("Email") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { address = it },
                                    label = { Text("Street Address") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State") }, modifier = Modifier.weight(1f))
                                }
                                OutlinedTextField(
                                    value = pinCode,
                                    onValueChange = { pinCode = it },
                                    label = { Text("PIN Code") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            3 -> {
                                Text("Select Primary Business Type", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BusinessCategory.entries.forEach { cat ->
                                        FilterChip(
                                            selected = category == cat,
                                            onClick = {
                                                category = cat
                                                enableWholesale = cat == BusinessCategory.WHOLESALE || cat == BusinessCategory.DISTRIBUTOR
                                                enableMedical = cat == BusinessCategory.MEDICAL
                                                enableGarments = cat == BusinessCategory.CLOTHING
                                                enableElectronics = cat == BusinessCategory.ELECTRONICS
                                                enableRestaurant = cat == BusinessCategory.RESTAURANT
                                                enableManufacturing = cat == BusinessCategory.MANUFACTURER
                                                enableService = cat == BusinessCategory.SERVICE
                                            },
                                            label = { Text(cat.label) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Optional Industry Modules", style = MaterialTheme.typography.titleMedium)
                                ModuleSwitch("Wholesale Slabs & Bulk Pricing", enableWholesale) { enableWholesale = it }
                                ModuleSwitch("Medical Batch & Expiry Tracking", enableMedical) { enableMedical = it }
                                ModuleSwitch("Garments Size, Color & Style", enableGarments) { enableGarments = it }
                                ModuleSwitch("Electronics Serial & Warranty", enableElectronics) { enableElectronics = it }
                                ModuleSwitch("Restaurant Tables & KOT", enableRestaurant) { enableRestaurant = it }
                                ModuleSwitch("Manufacturing Raw Material Conversion", enableManufacturing) { enableManufacturing = it }
                                ModuleSwitch("Service Business Records", enableService) { enableService = it }
                            }
                            4 -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("GST Registered Business", style = MaterialTheme.typography.titleMedium)
                                        Text("Enable CGST / SGST / IGST tax billing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = isGstRegistered, onCheckedChange = { isGstRegistered = it })
                                }
                                if (isGstRegistered) {
                                    OutlinedTextField(
                                        value = gstin,
                                        onValueChange = { gstin = it.uppercase(); errorMsg = null },
                                        label = { Text("15-Digit GSTIN (Optional)") },
                                        supportingText = { Text("Example: 27ABCDE1234F1Z5") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = financialYear,
                                        onValueChange = { financialYear = it },
                                        label = { Text("Financial Year") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = currencySymbol,
                                        onValueChange = { currencySymbol = it.take(3) },
                                        label = { Text("Currency") },
                                        modifier = Modifier.weight(0.6f)
                                    )
                                }
                            }
                            5 -> {
                                OutlinedTextField(
                                    value = invoicePrefix,
                                    onValueChange = { invoicePrefix = it.uppercase() },
                                    label = { Text("Invoice Prefix") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = nextInvoiceNumber,
                                    onValueChange = { if (it.all(Char::isDigit)) nextInvoiceNumber = it },
                                    label = { Text("Starting Invoice Number") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = terms,
                                    onValueChange = { terms = it },
                                    label = { Text("Default Invoice Terms & Conditions") },
                                    minLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            6 -> {
                                Text("Choose Your Visual Theme", style = MaterialTheme.typography.titleMedium)
                                AppThemeMode.entries.forEach { mode ->
                                    val selected = mode == currentTheme
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                shape = MaterialTheme.shapes.medium
                                            )
                                            .clickable { onThemeChanged(mode) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(mode.label, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        when (mode) {
                                                            AppThemeMode.LIGHT -> "Crisp white cards with warm orange focal accents"
                                                            AppThemeMode.DARK -> "High-contrast charcoal dark mode for low light"
                                                            AppThemeMode.ORANGE -> "Bold orange commercial branding throughout"
                                                        },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (selected) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                            7 -> {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp))
                                        Text("Ready to Launch!", style = MaterialTheme.typography.headlineMedium)
                                        Text("Business: ${businessName.ifBlank { "My Store" }}", style = MaterialTheme.typography.titleMedium)
                                        Text("Category: ${category.label} • ${if (isGstRegistered) "GST Enabled" else "Non-GST"}")
                                        Text("Invoice Series: $invoicePrefix${nextInvoiceNumber.ifBlank { "1001" }}")
                                        Text("Theme: ${currentTheme.label}")
                                    }
                                }
                            }
                        }
                    }
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) { Text("Back") }
                }
                Button(
                    onClick = {
                        when (step) {
                            2 -> {
                                if (businessName.isBlank()) {
                                    businessName = "My Business"
                                }
                                step++
                            }
                            4 -> {
                                if (isGstRegistered && gstin.isNotBlank() && !GstCalculator.isValidGstin(gstin)) {
                                    errorMsg = "GSTIN format appears invalid (15 characters expected, e.g. 27ABCDE1234F1Z5)"
                                } else {
                                    step++
                                }
                            }
                            7 -> {
                                scope.launch {
                                    val profile = BusinessProfile(
                                        id = 1L,
                                        businessName = businessName.ifBlank { "My Business" },
                                        ownerName = ownerName.trim(),
                                        logoUri = logoUri,
                                        phone = phone.trim(),
                                        email = email.trim(),
                                        address = address.trim(),
                                        city = city.trim(),
                                        state = state.trim(),
                                        pinCode = pinCode.trim(),
                                        category = category,
                                        isGstRegistered = isGstRegistered,
                                        gstin = gstin.trim().uppercase(),
                                        financialYear = financialYear.ifBlank { DateUtils.currentFinancialYearLabel() },
                                        currencySymbol = currencySymbol.ifBlank { "₹" },
                                        invoicePrefix = invoicePrefix.ifBlank { "INV-" },
                                        nextInvoiceNumber = nextInvoiceNumber.toLongOrNull() ?: 1001L,
                                        defaultTerms = terms
                                    )
                                    app.container.businessRepository.saveBusinessProfile(profile, completeOnboarding = true)
                                    app.container.businessRepository.updateAppSettings {
                                        it.copy(
                                            themeMode = currentTheme,
                                            enableWholesaleModule = enableWholesale,
                                            enableMedicalModule = enableMedical,
                                            enableGarmentsModule = enableGarments,
                                            enableElectronicsModule = enableElectronics,
                                            enableRestaurantModule = enableRestaurant,
                                            enableManufacturingModule = enableManufacturing,
                                            enableServiceModule = enableService
                                        )
                                    }
                                    onSetupCompleted()
                                }
                            }
                            else -> step++
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp).testTag("setup_next_button")
                ) {
                    Text(if (step == 7) "Start Using HisabPulse" else "Continue")
                }
            }
        }
    }
}

@Composable
private fun ModuleSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun InteractiveTutorialScreen(onBack: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val step = HISAB_TUTORIAL_STEPS[index]

    Scaffold(
        topBar = {
            HisabPulseTopBar(title = "Interactive Feature Guide", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LinearProgressIndicator(
                    progress = { (index + 1) / HISAB_TUTORIAL_STEPS.size.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                StatusPill(
                    label = "${step.highlightArea} • Step ${index + 1} of ${HISAB_TUTORIAL_STEPS.size}",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Text(step.title, style = MaterialTheme.typography.headlineMedium)
                        Text(step.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (index > 0) index-- else onBack() },
                    modifier = Modifier.weight(1f)
                ) { Text(if (index == 0) "Close" else "Back") }
                Button(
                    onClick = {
                        if (index < HISAB_TUTORIAL_STEPS.lastIndex) index++ else onBack()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (index == HISAB_TUTORIAL_STEPS.lastIndex) "Finish" else "Next") }
            }
        }
    }
}
