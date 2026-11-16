package com.example.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.HisabPulseApp
import com.example.core.BrandingAssetManager
import com.example.core.PdfGenerator
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.InvoiceTemplate
import com.example.data.local.entities.PaymentMode
import com.example.data.repository.UserProfile
import com.example.data.repository.UserProfileRepository
import com.example.ui.components.HisabPulseTopBar
import com.example.ui.components.SignaturePadDialog
import com.example.ui.screens.settings.studio.CORE_TEMPLATE_CODES
import com.example.ui.screens.settings.studio.InvoiceStudioPreviewCard
import com.example.ui.screens.settings.studio.StudioBrandingTab
import com.example.ui.screens.settings.studio.StudioCopyAndSaveTab
import com.example.ui.screens.settings.studio.StudioEditableBusiness
import com.example.ui.screens.settings.studio.StudioSectionsTab
import com.example.ui.screens.settings.studio.StudioStyleTab
import com.example.ui.screens.settings.studio.StudioTab
import com.example.ui.screens.settings.studio.toFullAddressString
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TemplateDesignerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val templates by app.container.businessRepository.invoiceTemplates.collectAsStateWithLifecycle(emptyList())
    val profile by app.container.businessRepository.businessProfile.collectAsStateWithLifecycle(null)
    val selected = templates.firstOrNull { it.isSelected } ?: templates.firstOrNull()

    var workingTemplate by remember { mutableStateOf<InvoiceTemplate?>(null) }
    var workingBusiness by remember { mutableStateOf(StudioEditableBusiness()) }
    var customHexInput by remember { mutableStateOf("#0F2D5C") }
    var secondaryHexInput by remember { mutableStateOf("#0284C7") }
    var activeTab by remember { mutableStateOf(StudioTab.STYLE) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }
    var initializedBiz by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                BrandingAssetManager.saveImageUriToInternalPng(context, uri, "logo", maxDimensionPx = 512)
                    .onSuccess { localUri ->
                        workingBusiness = workingBusiness.copy(logoUri = localUri)
                        snackbar.showSnackbar("Shop logo updated")
                    }
                    .onFailure {
                        snackbar.showSnackbar(it.message ?: "Could not load selected logo")
                    }
            }
        }
    }

    val signaturePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                BrandingAssetManager.saveImageUriToInternalPng(context, uri, "signature", maxDimensionPx = 512)
                    .onSuccess { localUri ->
                        workingBusiness = workingBusiness.copy(signatureUri = localUri)
                        snackbar.showSnackbar("Signature image uploaded")
                    }
                    .onFailure {
                        snackbar.showSnackbar(it.message ?: "Could not load signature image")
                    }
            }
        }
    }

    val sealPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                BrandingAssetManager.saveImageUriToInternalPng(context, uri, "seal", maxDimensionPx = 384)
                    .onSuccess { localUri ->
                        workingBusiness = workingBusiness.copy(sealUri = localUri)
                        snackbar.showSnackbar("Shop seal/stamp uploaded")
                    }
                    .onFailure {
                        snackbar.showSnackbar(it.message ?: "Could not load seal image")
                    }
            }
        }
    }

    LaunchedEffect(selected?.id) {
        if (selected != null && (workingTemplate == null || workingTemplate?.id != selected.id)) {
            workingTemplate = selected
            customHexInput = selected.accentColorHex
            secondaryHexInput = selected.secondaryColorHex
        }
    }

    LaunchedEffect(profile) {
        if (profile != null && !initializedBiz) {
            workingBusiness = StudioEditableBusiness.fromProfile(profile)
            initializedBiz = true
        }
    }

    suspend fun persistBusinessProfileChanges(): BusinessProfile {
        val base = profile ?: BusinessProfile(businessName = workingBusiness.businessName.ifBlank { "My Business" })
            val keepParts = workingBusiness.address == base.toFullAddressString()
        val updated = base.copy(
            businessName = workingBusiness.businessName.ifBlank { "My Business" },
            tagline = workingBusiness.tagline,
            logoUri = workingBusiness.logoUri,
            phone = workingBusiness.phone,
            email = workingBusiness.email,
            website = workingBusiness.website,
            address = workingBusiness.address,
            city = if (keepParts) base.city else "",
            state = if (keepParts) base.state else "",
            pinCode = if (keepParts) base.pinCode else "",
            gstin = workingBusiness.gstin,
            upiId = workingBusiness.upiId,
            signatureLabel = workingBusiness.signatureLabel.ifBlank { "Authorized Signature" },
            signatureUri = workingBusiness.signatureUri,
            sealUri = workingBusiness.sealUri
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
        return updated
    }

    val tpl = workingTemplate

    Scaffold(
        topBar = {
            HisabPulseTopBar(
                title = "Invoice Design Studio",
                subtitle = "${templates.size} templates • Live preview & custom colors",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (tpl == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Loading Invoice Design Studio...")
            }
            return@Scaffold
        }

        val isCustomTemplate = tpl.code !in CORE_TEMPLATE_CODES || templates.count { it.code == tpl.code } > 1

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "tpl_selector") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Saved Templates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = {
                        newTemplateName = "${tpl.name} Custom"
                        showSaveAsDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Save As New")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { item ->
                        FilterChip(
                            selected = item.id == tpl.id,
                            onClick = {
                                workingTemplate = item
                                customHexInput = item.accentColorHex
                                secondaryHexInput = item.secondaryColorHex
                                scope.launch { app.container.businessRepository.selectInvoiceTemplate(item.id) }
                            },
                            leadingIcon = if (item.id == tpl.id) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text(item.name) }
                        )
                    }
                }
            }

            item(key = "live_preview") {
                InvoiceStudioPreviewCard(
                    template = tpl,
                    business = workingBusiness
                )
            }

            item(key = "studio_tabs") {
                TabRow(selectedTabIndex = activeTab.ordinal) {
                    StudioTab.entries.forEach { tab ->
                        Tab(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            text = { Text(tab.label, maxLines = 1) }
                        )
                    }
                }
            }

            item(key = "tab_content") {
                when (activeTab) {
                    StudioTab.STYLE -> StudioStyleTab(
                        template = tpl,
                        customHexInput = customHexInput,
                        secondaryHexInput = secondaryHexInput,
                        onCustomHexChange = { customHexInput = it },
                        onSecondaryHexChange = { secondaryHexInput = it },
                        onTemplateChange = { workingTemplate = it }
                    )

                    StudioTab.BRANDING -> StudioBrandingTab(
                        business = workingBusiness,
                        onBusinessChange = { workingBusiness = it },
                        onPickLogo = {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onDrawSignature = { showSignaturePad = true },
                        onPickSignatureImage = {
                            signaturePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onPickSealImage = {
                            sealPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSaveBusinessDetails = {
                            scope.launch {
                                persistBusinessProfileChanges()
                                snackbar.showSnackbar("Logo, signature & shop details saved for all invoices")
                            }
                        }
                    )

                    StudioTab.SECTIONS -> StudioSectionsTab(
                        template = tpl,
                        onTemplateChange = { workingTemplate = it }
                    )

                    StudioTab.COPY -> StudioCopyAndSaveTab(
                        template = tpl,
                        isCustomTemplate = isCustomTemplate,
                        isExportingPdf = isExportingPdf,
                        onTemplateChange = { workingTemplate = it },
                        onSaveAndActivate = {
                            scope.launch {
                                persistBusinessProfileChanges()
                                app.container.businessRepository.updateInvoiceTemplate(tpl)
                                app.container.businessRepository.selectInvoiceTemplate(tpl.id)
                                snackbar.showSnackbar("Saved & activated ${tpl.name}")
                            }
                        },
                        onOpenSaveAsNew = {
                            newTemplateName = "${tpl.name} Custom"
                            showSaveAsDialog = true
                        },
                        onPreviewSamplePdf = {
                            if (isExportingPdf) return@StudioCopyAndSaveTab
                            isExportingPdf = true
                            scope.launch {
                                runCatching {
                                    val updatedBiz = persistBusinessProfileChanges()
                                    app.container.businessRepository.updateInvoiceTemplate(tpl)
                                    val sampleInvoice = Invoice(
                                        id = 0L,
                                        invoiceNumber = "INV-SAMPLE-01",
                                        documentType = InvoiceDocumentType.TAX_INVOICE,
                                        status = InvoiceStatus.PAID,
                                        customerName = "Aarav Traders",
                                        customerPhone = "9876543210",
                                        customerAddress = "Market Road, Pune",
                                        customerGstin = "27XYZAB5678G1Z2",
                                        subtotalPaise = 240_00L,
                                        itemDiscountPaise = 12_00L,
                                        taxableAmountPaise = 228_00L,
                                        cgstPaise = 20_52L,
                                        sgstPaise = 20_52L,
                                        totalTaxPaise = 41_04L,
                                        grandTotalPaise = 269_04L,
                                        paidAmountPaise = 269_04L,
                                        balanceDuePaise = 0L,
                                        paymentMode = PaymentMode.UPI,
                                        notes = "Thank you for visiting our shop!"
                                    )
                                    val sampleItems = listOf(
                                        InvoiceItem(
                                            invoiceId = 0L,
                                            productName = "LED Bulb 9W",
                                            hsnSacCode = "8539",
                                            unitName = "PCS",
                                            quantityScaled = 200L,
                                            ratePaise = 120_00L,
                                            discountPercentScaled = 500L,
                                            discountAmountPaise = 12_00L,
                                            taxRatePercentScaled = 1800L,
                                            taxableAmountPaise = 228_00L,
                                            cgstPaise = 20_52L,
                                            sgstPaise = 20_52L,
                                            lineTotalPaise = 269_04L
                                        )
                                    )
                                    withContext(Dispatchers.IO) {
                                        PdfGenerator.generateInvoicePdf(context, updatedBiz, sampleInvoice, sampleItems, tpl)
                                    }
                                }.onSuccess { pdfFile ->
                                    isExportingPdf = false
                                    PdfGenerator.shareFile(context, pdfFile, "application/pdf", "Preview Sample Invoice PDF")
                                }.onFailure { err ->
                                    isExportingPdf = false
                                    snackbar.showSnackbar(err.message ?: "Failed to generate PDF")
                                }
                            }
                        },
                        onDeleteCustom = {
                            scope.launch {
                                app.container.businessRepository.deleteCustomInvoiceTemplate(tpl)
                                workingTemplate = null
                                snackbar.showSnackbar("Removed custom template")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSaved = { savedUri ->
                workingBusiness = workingBusiness.copy(signatureUri = savedUri)
                showSignaturePad = false
                scope.launch {
                    persistBusinessProfileChanges()
                    snackbar.showSnackbar("Signature saved & applied to invoices")
                }
            }
        )
    }

    if (showSaveAsDialog && tpl != null) {
        AlertDialog(
            onDismissRequest = { showSaveAsDialog = false },
            title = { Text("Save as New Custom Template") },
            text = {
                OutlinedTextField(
                    value = newTemplateName,
                    onValueChange = { newTemplateName = it },
                    label = { Text("Template Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosenName = newTemplateName.trim().ifBlank { "${tpl.name} Copy" }
                        scope.launch {
                            persistBusinessProfileChanges()
                            val baseCode = if (tpl.code.startsWith("CORPORATE_WAVE")) "CORPORATE_WAVE_CUSTOM" else "CUSTOM"
                            val newId = app.container.businessRepository.createCustomInvoiceTemplate(
                                tpl.copy(
                                    id = 0L,
                                    name = chosenName,
                                    code = "${baseCode}_${System.currentTimeMillis()}"
                                )
                            )
                            workingTemplate = tpl.copy(id = newId, name = chosenName, isSelected = true)
                            showSaveAsDialog = false
                            snackbar.showSnackbar("Created & activated '$chosenName'")
                        }
                    }
                ) { Text("Save New") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveAsDialog = false }) { Text("Cancel") }
            }
        )
    }
}
