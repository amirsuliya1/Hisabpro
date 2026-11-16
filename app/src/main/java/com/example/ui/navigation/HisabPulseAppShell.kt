package com.example.ui.navigation

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.HisabPulseApp
import com.example.data.local.entities.AppThemeMode
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.preferences.SecurityPreferences
import com.example.data.preferences.TutorialPreferences
import com.example.ui.components.InteractiveTutorialOverlay
import com.example.ui.screens.billing.BillingScreen
import com.example.ui.screens.billing.CreateEditInvoiceScreen
import com.example.ui.screens.billing.FilteredDocumentListScreen
import com.example.ui.screens.billing.InvoiceDetailScreen
import com.example.ui.screens.billing.InvoicePreviewScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.settings.LegalPrivacyScreen
import com.example.ui.screens.dashboard.SalesOverviewScreen
import com.example.ui.screens.dataops.CsvImportExportScreen
import com.example.ui.screens.dataops.UniversalRecoveryScreen
import com.example.ui.screens.finance.AccountingScreen
import com.example.ui.screens.finance.BalanceSheetScreen
import com.example.ui.screens.finance.BankAccountsScreen
import com.example.ui.screens.finance.CashbookScreen
import com.example.ui.screens.finance.ExpensesScreen
import com.example.ui.screens.finance.JournalEntriesScreen
import com.example.ui.screens.finance.PaymentsScreen
import com.example.ui.screens.finance.ProfitAndLossScreen
import com.example.ui.screens.finance.TrialBalanceScreen
import com.example.ui.screens.intelligence.BatchExpiryScreen
import com.example.ui.screens.intelligence.BusinessHealthScreen
import com.example.ui.screens.intelligence.PriceHistoryScreen
import com.example.ui.screens.intelligence.ProfitIntelligenceScreen
import com.example.ui.screens.intelligence.StockPredictionScreen
import com.example.ui.screens.items.AddEditProductScreen
import com.example.ui.screens.items.CategoriesScreen
import com.example.ui.screens.items.InventoryScreen
import com.example.ui.screens.items.ProductDetailScreen
import com.example.ui.screens.items.ProductsScreen
import com.example.ui.screens.items.StockAdjustmentScreen
import com.example.ui.screens.items.StockTransferScreen
import com.example.ui.screens.items.UnitsScreen
import com.example.ui.screens.onboarding.BusinessSetupScreen
import com.example.ui.screens.onboarding.InteractiveTutorialScreen
import com.example.ui.screens.onboarding.SplashScreen
import com.example.ui.screens.onboarding.WelcomeScreen
import com.example.ui.screens.operations.BusinessSwitcherScreen
import com.example.ui.screens.operations.DailyClosingScreen
import com.example.ui.screens.operations.LoyaltyProgramScreen
import com.example.ui.screens.operations.PaymentRemindersScreen
import com.example.ui.screens.operations.PrinterSettingsScreen
import com.example.ui.screens.operations.UpiSettingsScreen
import com.example.ui.screens.operations.VoiceAssistantScreen
import com.example.ui.screens.parties.CustomerDetailScreen
import com.example.ui.screens.parties.CustomerLedgerScreen
import com.example.ui.screens.parties.CustomersScreen
import com.example.ui.screens.parties.SupplierDetailScreen
import com.example.ui.screens.parties.SupplierLedgerScreen
import com.example.ui.screens.parties.SuppliersScreen
import com.example.ui.screens.pos.PosScreen
import com.example.ui.screens.purchases.CreatePurchaseScreen
import com.example.ui.screens.purchases.PurchaseDetailScreen
import com.example.ui.screens.purchases.PurchasesScreen
import com.example.ui.screens.reports.BusinessReportsScreen
import com.example.ui.screens.reports.GstReportsScreen
import com.example.ui.screens.settings.AboutScreen
import com.example.ui.screens.settings.AppSettingsScreen
import com.example.ui.screens.settings.BackupScreen
import com.example.ui.screens.settings.BusinessProfileScreen
import com.example.ui.screens.settings.HelpScreen
import com.example.ui.screens.settings.InvoiceTemplatesScreen
import com.example.ui.screens.settings.MoreMenuScreen
import com.example.ui.screens.settings.RestoreScreen
import com.example.ui.screens.settings.SecurityScreen
import com.example.ui.screens.settings.TemplateDesignerScreen
import com.example.ui.screens.settings.ThemeSelectionScreen
import com.example.ui.theme.HisabPulseTheme
import kotlinx.coroutines.launch

private data class PrimaryTabItem(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private val PRIMARY_TABS = listOf(
    PrimaryTabItem(AppRoute.Dashboard, "Home", Icons.Default.Home, "tab_home"),
    PrimaryTabItem(AppRoute.Billing, "Billing", Icons.Default.ReceiptLong, "tab_billing"),
    PrimaryTabItem(AppRoute.Products, "Items", Icons.Default.Inventory2, "tab_items"),
    PrimaryTabItem(AppRoute.Customers, "Parties", Icons.Default.People, "tab_parties"),
    PrimaryTabItem(AppRoute.MoreMenu, "More", Icons.Default.Apps, "tab_more")
)

@Composable
fun HisabPulseAppShell() {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeMode by app.container.preferences.themeMode.collectAsStateWithLifecycle(AppThemeMode.LIGHT)
    val secPrefs by app.container.preferences.securityPreferences.collectAsStateWithLifecycle(SecurityPreferences())
    val tutorialPrefs by app.container.preferences.tutorialPreferences.collectAsStateWithLifecycle(TutorialPreferences())

    var unlocked by rememberSaveable {
        mutableStateOf(!app.container.preferences.isPinEnabledSync())
    }
    var initializedLock by rememberSaveable {
        mutableStateOf(app.container.preferences.isPinEnabledSync())
    }
    var showTutorialOverlay by remember { mutableStateOf(false) }
    var tutorialStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        app.container.cloudSyncRepository.startSyncForCurrentUser()
    }

    LaunchedEffect(secPrefs.pinEnabled) {
        if (!initializedLock && secPrefs.pinEnabled) {
            unlocked = false
            initializedLock = true
        } else if (!secPrefs.pinEnabled) {
            unlocked = true
            initializedLock = true
        }
    }

    DisposableEffect(lifecycleOwner, secPrefs.pinEnabled, secPrefs.autoLockMinutes, secPrefs.lastBackgroundMillis) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    scope.launch {
                        app.container.preferences.updateLastBackgroundMillis(System.currentTimeMillis())
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (secPrefs.pinEnabled && secPrefs.lastBackgroundMillis > 0L) {
                        val elapsed = System.currentTimeMillis() - secPrefs.lastBackgroundMillis
                        if (elapsed >= secPrefs.autoLockMinutes * 60_000L) {
                            unlocked = false
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HisabPulseTheme(themeMode = themeMode) {
        if (secPrefs.pinEnabled && !unlocked) {
            AppLockScreen(
                biometricEnabled = secPrefs.biometricEnabled,
                onVerifyPin = { pin -> app.container.preferences.verifyPin(pin) },
                onUnlocked = { unlocked = true }
            )
            return@HisabPulseTheme
        }

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        val isPrimaryTab = PRIMARY_TABS.any { tab ->
            currentDestination?.hasRoute(tab.route::class) == true
        }

        fun navigateTopTab(route: AppRoute) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun navigateByMenuKey(key: String) {
            when (key) {
                "voice_assistant" -> navController.navigate(AppRoute.VoiceAssistant)
                "daily_closing" -> navController.navigate(AppRoute.DailyClosing)
                "business_switcher" -> navController.navigate(AppRoute.BusinessSwitcher)
                "stock_prediction" -> navController.navigate(AppRoute.StockPrediction)
                "batch_expiry" -> navController.navigate(AppRoute.BatchExpiryTracking)
                "price_history" -> navController.navigate(AppRoute.PriceHistory)
                "inventory" -> navController.navigate(AppRoute.Inventory)
                "payment_reminders" -> navController.navigate(AppRoute.PaymentReminders)
                "loyalty_program" -> navController.navigate(AppRoute.LoyaltyProgram)
                "pos" -> navController.navigate(AppRoute.Pos)
                "estimates" -> navController.navigate(AppRoute.Estimates)
                "purchases" -> navController.navigate(AppRoute.Purchases)
                "suppliers" -> navController.navigate(AppRoute.Suppliers)
                "templates" -> navController.navigate(AppRoute.TemplateDesigner)
                "printer_settings" -> navController.navigate(AppRoute.PrinterSettings)
                "upi_settings" -> navController.navigate(AppRoute.UpiSettings)
                "profit_intelligence" -> navController.navigate(AppRoute.ProfitIntelligence)
                "business_health" -> navController.navigate(AppRoute.BusinessHealth)
                "gst_reports" -> navController.navigate(AppRoute.GstReports)
                "business_reports" -> navController.navigate(AppRoute.BusinessReports)
                "csv_import_export" -> navController.navigate(AppRoute.CsvImportExport)
                "universal_recovery" -> navController.navigate(AppRoute.UniversalRecovery)
                "backup" -> navController.navigate(AppRoute.Backup)
                "restore" -> navController.navigate(AppRoute.Restore)
                "payments" -> navController.navigate(AppRoute.Payments)
                "cashbook" -> navController.navigate(AppRoute.Cashbook)
                "expenses" -> navController.navigate(AppRoute.Expenses)
                "accounting" -> navController.navigate(AppRoute.Accounting)
                "banks" -> navController.navigate(AppRoute.BankAccounts)
                "profile" -> navController.navigate(AppRoute.BusinessProfile)
                "security" -> navController.navigate(AppRoute.Security)
                "legal_privacy" -> navController.navigate(AppRoute.LegalPrivacy)
                "theme" -> navController.navigate(AppRoute.ThemeSelection)
                "settings" -> navController.navigate(AppRoute.AppSettings)
                "help" -> navController.navigate(AppRoute.Help)
                "about" -> navController.navigate(AppRoute.About)
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 700.dp

            Scaffold(
                bottomBar = {
                    if (isPrimaryTab && !useRail) {
                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            PRIMARY_TABS.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (isPrimaryTab && useRail) {
                        NavigationRail {
                            PRIMARY_TABS.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        val startRoute = remember {
                            if (app.container.preferences.isOnboardingCompletedSync()) {
                                AppRoute.Dashboard
                            } else {
                                AppRoute.Splash
                            }
                        }
                        NavHost(
                            navController = navController,
                            startDestination = startRoute
                        ) {
                            composable<AppRoute.Splash> {
                                SplashScreen(
                                    onNavigateToWelcome = {
                                        navController.navigate(AppRoute.Welcome) {
                                            popUpTo(AppRoute.Splash) { inclusive = true }
                                        }
                                    },
                                    onNavigateToDashboard = {
                                        navController.navigate(AppRoute.Dashboard) {
                                            popUpTo(AppRoute.Splash) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable<AppRoute.Welcome> {
                                WelcomeScreen(onStartSetup = { navController.navigate(AppRoute.BusinessSetup) })
                            }
                            composable<AppRoute.BusinessSetup> {
                                BusinessSetupScreen(
                                    currentTheme = themeMode,
                                    onThemeChanged = { m -> scope.launch { app.container.businessRepository.setTheme(m) } },
                                    onSetupCompleted = {
                                        navController.navigate(AppRoute.Dashboard) {
                                            popUpTo(AppRoute.Welcome) { inclusive = true }
                                        }
                                        if (!tutorialPrefs.completed) {
                                            tutorialStep = 0
                                            showTutorialOverlay = true
                                        }
                                    }
                                )
                            }

                            // 5 Primary Tabs
                            composable<AppRoute.Dashboard> {
                                DashboardScreen(
                                    onOpenSalesOverview = { navController.navigate(AppRoute.SalesOverview) },
                                    onOpenPurchases = { navController.navigate(AppRoute.Purchases) },
                                    onOpenExpenses = { navController.navigate(AppRoute.Expenses) },
                                    onOpenProfitAndLoss = { navController.navigate(AppRoute.ProfitAndLoss) },
                                    onOpenCustomers = { navigateTopTab(AppRoute.Customers) },
                                    onOpenSuppliers = { navController.navigate(AppRoute.Suppliers) },
                                    onOpenCashbook = { navController.navigate(AppRoute.Cashbook) },
                                    onOpenBankAccounts = { navController.navigate(AppRoute.BankAccounts) },
                                    onOpenInventory = { navController.navigate(AppRoute.Inventory) },
                                    onOpenCreateInvoice = { navController.navigate(AppRoute.CreateInvoice()) },
                                    onOpenPos = { navController.navigate(AppRoute.Pos) },
                                    onOpenPayments = { navController.navigate(AppRoute.Payments) },
                                    onOpenInvoiceDetail = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) },
                                    onOpenJournalEntries = { navController.navigate(AppRoute.JournalEntries) },
                                    onOpenReports = { navController.navigate(AppRoute.BusinessReports) }
                                )
                            }
                            composable<AppRoute.SalesOverview> {
                                SalesOverviewScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenInvoiceDetail = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) },
                                    onCreateInvoice = { navController.navigate(AppRoute.CreateInvoice()) }
                                )
                            }

                            composable<AppRoute.Billing> {
                                BillingScreen(
                                    onCreateInvoice = { type -> navController.navigate(AppRoute.CreateInvoice(documentType = type.name)) },
                                    onEditDraft = { id, type -> navController.navigate(AppRoute.CreateInvoice(invoiceId = id, documentType = type.name)) },
                                    onOpenInvoiceDetail = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) },
                                    onOpenHistory = { navController.navigate(AppRoute.InvoiceHistory) },
                                    onOpenEstimates = { navController.navigate(AppRoute.Estimates) },
                                    onOpenQuotations = { navController.navigate(AppRoute.Quotations) },
                                    onOpenSalesReturns = { navController.navigate(AppRoute.SalesReturns) },
                                    onOpenPos = { navController.navigate(AppRoute.Pos) }
                                )
                            }
                            composable<AppRoute.CreateInvoice> { entry ->
                                val args = entry.toRoute<AppRoute.CreateInvoice>()
                                CreateEditInvoiceScreen(
                                    existingInvoiceId = args.invoiceId,
                                    initialDocumentType = args.documentType,
                                    presetCustomerId = args.presetCustomerId,
                                    onBack = { navController.popBackStack() },
                                    onSaved = { savedId ->
                                        navController.navigate(AppRoute.InvoiceDetails(savedId)) {
                                            popUpTo(entry.destination.id) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable<AppRoute.InvoiceDetails> { entry ->
                                val args = entry.toRoute<AppRoute.InvoiceDetails>()
                                InvoiceDetailScreen(
                                    invoiceId = args.invoiceId,
                                    onBack = { navController.popBackStack() },
                                    onOpenPreview = { id -> navController.navigate(AppRoute.InvoicePreview(id)) },
                                    onEditDraft = { id, type -> navController.navigate(AppRoute.CreateInvoice(invoiceId = id, documentType = type.name)) },
                                    onOpenNewInvoice = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) }
                                )
                            }
                            composable<AppRoute.InvoicePreview> { entry ->
                                val args = entry.toRoute<AppRoute.InvoicePreview>()
                                InvoicePreviewScreen(invoiceId = args.invoiceId, onBack = { navController.popBackStack() })
                            }
                            composable<AppRoute.InvoiceHistory> {
                                FilteredDocumentListScreen(
                                    title = "Invoice History",
                                    documentTypes = null,
                                    createType = InvoiceDocumentType.TAX_INVOICE,
                                    onBack = { navController.popBackStack() },
                                    onCreate = { type -> navController.navigate(AppRoute.CreateInvoice(documentType = type.name)) },
                                    onEditDraft = { id, type -> navController.navigate(AppRoute.CreateInvoice(invoiceId = id, documentType = type.name)) },
                                    onOpenDetail = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) }
                                )
                            }
                            composable<AppRoute.Estimates> {
                                FilteredDocumentListScreen(
                                    title = "Estimates",
                                    documentTypes = setOf(InvoiceDocumentType.ESTIMATE, InvoiceDocumentType.PROFORMA),
                                    createType = InvoiceDocumentType.ESTIMATE,
                                    onBack = { navController.popBackStack() },
                                    onCreate = { type -> navController.navigate(AppRoute.CreateInvoice(documentType = type.name)) },
                                    onEditDraft = { id, type -> navController.navigate(AppRoute.CreateInvoice(invoiceId = id, documentType = type.name)) },
                                    onOpenDetail = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) }
                                )
                            }
                            composable<AppRoute.Quotations> {
                                FilteredDocumentListScreen(
                                    title = "Quotations",
                                    documentTypes = setOf(InvoiceDocumentType.QUOTATION),
                                    createType = InvoiceDocumentType.QUOTATION,
                                    onBack = { navController.popBackStack() },
                                    onCreate = { type -> navController.navigate(AppRoute.CreateInvoice(documentType = type.name)) },
                                    onEditDraft = { id, type -> navController.navigate(AppRoute.CreateInvoice(invoiceId = id, documentType = type.name)) },
                                    onOpenDetail = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) }
                                )
                            }
                            composable<AppRoute.SalesReturns> {
                                FilteredDocumentListScreen(
                                    title = "Sales Returns & Credit Notes",
                                    documentTypes = setOf(InvoiceDocumentType.SALES_RETURN, InvoiceDocumentType.CREDIT_NOTE),
                                    createType = InvoiceDocumentType.SALES_RETURN,
                                    onBack = { navController.popBackStack() },
                                    onCreate = { type -> navController.navigate(AppRoute.CreateInvoice(documentType = type.name)) },
                                    onEditDraft = { id, type -> navController.navigate(AppRoute.CreateInvoice(invoiceId = id, documentType = type.name)) },
                                    onOpenDetail = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) }
                                )
                            }

                            // Items & Inventory
                            composable<AppRoute.Products> {
                                ProductsScreen(
                                    onAddProduct = { navController.navigate(AppRoute.AddProduct) },
                                    onOpenProductDetail = { id -> navController.navigate(AppRoute.ProductDetails(id)) },
                                    onOpenInventory = { navController.navigate(AppRoute.Inventory) },
                                    onOpenCategories = { navController.navigate(AppRoute.Categories) },
                                    onOpenUnits = { navController.navigate(AppRoute.Units) }
                                )
                            }
                            composable<AppRoute.AddProduct> {
                                AddEditProductScreen(productId = null, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
                            }
                            composable<AppRoute.EditProduct> { entry ->
                                val args = entry.toRoute<AppRoute.EditProduct>()
                                AddEditProductScreen(productId = args.productId, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
                            }
                            composable<AppRoute.ProductDetails> { entry ->
                                val args = entry.toRoute<AppRoute.ProductDetails>()
                                ProductDetailScreen(
                                    productId = args.productId,
                                    onBack = { navController.popBackStack() },
                                    onEdit = { id -> navController.navigate(AppRoute.EditProduct(id)) },
                                    onAdjustStock = { id -> navController.navigate(AppRoute.StockAdjustment(id)) },
                                    onTransferStock = { id -> navController.navigate(AppRoute.StockTransfer(id)) }
                                )
                            }
                            composable<AppRoute.Categories> { CategoriesScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Units> { UnitsScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Inventory> {
                                InventoryScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenStockAdjustment = { id -> navController.navigate(AppRoute.StockAdjustment(id)) },
                                    onOpenStockTransfer = { id -> navController.navigate(AppRoute.StockTransfer(id)) },
                                    onOpenProductDetail = { id -> navController.navigate(AppRoute.ProductDetails(id)) }
                                )
                            }
                            composable<AppRoute.StockAdjustment> { entry ->
                                val args = entry.toRoute<AppRoute.StockAdjustment>()
                                StockAdjustmentScreen(presetProductId = args.productId, onBack = { navController.popBackStack() })
                            }
                            composable<AppRoute.StockTransfer> { entry ->
                                val args = entry.toRoute<AppRoute.StockTransfer>()
                                StockTransferScreen(presetProductId = args.productId, onBack = { navController.popBackStack() })
                            }

                            // Parties (Customers & Suppliers)
                            composable<AppRoute.Customers> {
                                CustomersScreen(
                                    onOpenCustomerDetail = { id -> navController.navigate(AppRoute.CustomerDetails(id)) },
                                    onOpenSuppliers = { navController.navigate(AppRoute.Suppliers) }
                                )
                            }
                            composable<AppRoute.CustomerDetails> { entry ->
                                val args = entry.toRoute<AppRoute.CustomerDetails>()
                                CustomerDetailScreen(
                                    customerId = args.customerId,
                                    onBack = { navController.popBackStack() },
                                    onOpenLedger = { id -> navController.navigate(AppRoute.CustomerLedger(id)) },
                                    onCreateInvoiceForCustomer = { id -> navController.navigate(AppRoute.CreateInvoice(presetCustomerId = id)) },
                                    onEditDraftInvoice = { invId, docType -> navController.navigate(AppRoute.CreateInvoice(invoiceId = invId, documentType = docType.name)) },
                                    onOpenInvoiceDetail = { invId -> navController.navigate(AppRoute.InvoiceDetails(invId)) }
                                )
                            }
                            composable<AppRoute.CustomerLedger> { entry ->
                                val args = entry.toRoute<AppRoute.CustomerLedger>()
                                CustomerLedgerScreen(customerId = args.customerId, onBack = { navController.popBackStack() })
                            }
                            composable<AppRoute.Suppliers> {
                                SuppliersScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenSupplierDetail = { id -> navController.navigate(AppRoute.SupplierDetails(id)) }
                                )
                            }
                            composable<AppRoute.SupplierDetails> { entry ->
                                val args = entry.toRoute<AppRoute.SupplierDetails>()
                                SupplierDetailScreen(
                                    supplierId = args.supplierId,
                                    onBack = { navController.popBackStack() },
                                    onOpenLedger = { id -> navController.navigate(AppRoute.SupplierLedger(id)) },
                                    onOpenPurchaseDetail = { pId -> navController.navigate(AppRoute.PurchaseDetails(pId)) },
                                    onCreatePurchase = { navController.navigate(AppRoute.CreatePurchase(false)) }
                                )
                            }
                            composable<AppRoute.SupplierLedger> { entry ->
                                val args = entry.toRoute<AppRoute.SupplierLedger>()
                                SupplierLedgerScreen(supplierId = args.supplierId, onBack = { navController.popBackStack() })
                            }

                            // Purchases
                            composable<AppRoute.Purchases> {
                                PurchasesScreen(
                                    onlyReturns = false,
                                    onBack = { navController.popBackStack() },
                                    onCreatePurchase = { isRet -> navController.navigate(AppRoute.CreatePurchase(isRet)) },
                                    onOpenPurchaseDetail = { id -> navController.navigate(AppRoute.PurchaseDetails(id)) },
                                    onOpenPurchaseReturns = { navController.navigate(AppRoute.PurchaseReturns) }
                                )
                            }
                            composable<AppRoute.PurchaseReturns> {
                                PurchasesScreen(
                                    onlyReturns = true,
                                    onBack = { navController.popBackStack() },
                                    onCreatePurchase = { navController.navigate(AppRoute.CreatePurchase(true)) },
                                    onOpenPurchaseDetail = { id -> navController.navigate(AppRoute.PurchaseDetails(id)) },
                                    onOpenPurchaseReturns = {}
                                )
                            }
                            composable<AppRoute.CreatePurchase> { entry ->
                                val args = entry.toRoute<AppRoute.CreatePurchase>()
                                CreatePurchaseScreen(
                                    isReturn = args.isReturn,
                                    onBack = { navController.popBackStack() },
                                    onSaved = { id ->
                                        navController.navigate(AppRoute.PurchaseDetails(id)) {
                                            popUpTo(entry.destination.id) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable<AppRoute.PurchaseDetails> { entry ->
                                val args = entry.toRoute<AppRoute.PurchaseDetails>()
                                PurchaseDetailScreen(purchaseId = args.purchaseId, onBack = { navController.popBackStack() })
                            }

                            // Finance & Accounting
                            composable<AppRoute.Payments> { PaymentsScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Cashbook> { CashbookScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Expenses> { ExpensesScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Accounting> {
                                AccountingScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenJournalEntries = { navController.navigate(AppRoute.JournalEntries) },
                                    onOpenTrialBalance = { navController.navigate(AppRoute.TrialBalance) },
                                    onOpenProfitAndLoss = { navController.navigate(AppRoute.ProfitAndLoss) },
                                    onOpenBalanceSheet = { navController.navigate(AppRoute.BalanceSheet) },
                                    onOpenBankAccounts = { navController.navigate(AppRoute.BankAccounts) }
                                )
                            }
                            composable<AppRoute.JournalEntries> { JournalEntriesScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.TrialBalance> { TrialBalanceScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.ProfitAndLoss> { ProfitAndLossScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.BalanceSheet> { BalanceSheetScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.BankAccounts> { BankAccountsScreen(onBack = { navController.popBackStack() }) }

                            // POS & Reports
                            composable<AppRoute.Pos> {
                                PosScreen(
                                    onBack = { navController.popBackStack() },
                                    onInvoiceGenerated = { id -> navController.navigate(AppRoute.InvoicePreview(id)) },
                                    onAddProduct = { navController.navigate(AppRoute.AddProduct) }
                                )
                            }
                            composable<AppRoute.GstReports> { GstReportsScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.BusinessReports> { BusinessReportsScreen(onBack = { navController.popBackStack() }) }

                            // More Menu, Enterprise Hub & Settings
                            composable<AppRoute.MoreMenu> {
                                MoreMenuScreen { key -> navigateByMenuKey(key) }
                            }
                            composable<AppRoute.VoiceAssistant> {
                                VoiceAssistantScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenInvoiceDetail = { id -> navController.navigate(AppRoute.InvoiceDetails(id)) },
                                    onOpenScreenRoute = { key -> navigateByMenuKey(key) }
                                )
                            }
                            composable<AppRoute.StockPrediction> { StockPredictionScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.ProfitIntelligence> { ProfitIntelligenceScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.BusinessHealth> { BusinessHealthScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.PaymentReminders> { PaymentRemindersScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.PrinterSettings> { PrinterSettingsScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.UpiSettings> { UpiSettingsScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.LoyaltyProgram> { LoyaltyProgramScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.BusinessSwitcher> { BusinessSwitcherScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.PriceHistory> { PriceHistoryScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.BatchExpiryTracking> { BatchExpiryScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.DailyClosing> { DailyClosingScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.CsvImportExport> { CsvImportExportScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.UniversalRecovery> { UniversalRecoveryScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.TemplateDesigner> { TemplateDesignerScreen(onBack = { navController.popBackStack() }) }

                            composable<AppRoute.InvoiceTemplates> { InvoiceTemplatesScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Backup> { BackupScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Restore> { RestoreScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Security> { SecurityScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.ThemeSelection> { ThemeSelectionScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.BusinessProfile> { BusinessProfileScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.AppSettings> { AppSettingsScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.InteractiveTutorial> { InteractiveTutorialScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.Help> {
                                HelpScreen(
                                    onBack = { navController.popBackStack() },
                                    onReplayTutorial = { navController.navigate(AppRoute.InteractiveTutorial) }
                                )
                            }
                            composable<AppRoute.LegalPrivacy> { LegalPrivacyScreen(onBack = { navController.popBackStack() }) }
                            composable<AppRoute.About> { AboutScreen(onBack = { navController.popBackStack() }) }
                        }

                        InteractiveTutorialOverlay(
                            visible = showTutorialOverlay,
                            stepIndex = tutorialStep,
                            onStepChange = { tutorialStep = it },
                            onDismiss = {
                                showTutorialOverlay = false
                                scope.launch { app.container.preferences.setTutorialCompleted(true, tutorialStep) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockScreen(
    biometricEnabled: Boolean,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock HisabPulse")
            .setSubtitle("Verify your identity to open your business books")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) triggerBiometric()
    }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("HisabPulse Locked", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { pin = it; error = null } },
                    label = { Text("Enter Owner PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        scope.launch {
                            if (onVerifyPin(pin)) onUnlocked()
                            else error = "Incorrect PIN"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Unlock") }
                if (biometricEnabled) {
                    OutlinedButton(onClick = { triggerBiometric() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Fingerprint, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Biometrics")
                    }
                }
            }
        }
    }
}
