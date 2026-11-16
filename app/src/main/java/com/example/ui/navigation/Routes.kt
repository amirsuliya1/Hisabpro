package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    // Onboarding
    @Serializable data object Splash : AppRoute
    @Serializable data object Welcome : AppRoute
    @Serializable data object BusinessSetup : AppRoute

    // 5 Primary Tabs
    @Serializable data object Dashboard : AppRoute
    @Serializable data object Billing : AppRoute
    @Serializable data object Products : AppRoute
    @Serializable data object Customers : AppRoute
    @Serializable data object MoreMenu : AppRoute

    // Dashboard & Sales
    @Serializable data object SalesOverview : AppRoute

    // Billing & Documents
    @Serializable data class CreateInvoice(
        val invoiceId: Long? = null,
        val documentType: String = "TAX_INVOICE",
        val presetCustomerId: Long? = null
    ) : AppRoute
    @Serializable data class InvoicePreview(val invoiceId: Long) : AppRoute
    @Serializable data class InvoiceDetails(val invoiceId: Long) : AppRoute
    @Serializable data object InvoiceHistory : AppRoute
    @Serializable data object Estimates : AppRoute
    @Serializable data object Quotations : AppRoute
    @Serializable data object SalesReturns : AppRoute

    // Parties
    @Serializable data class CustomerDetails(val customerId: Long) : AppRoute
    @Serializable data class CustomerLedger(val customerId: Long) : AppRoute
    @Serializable data object Suppliers : AppRoute
    @Serializable data class SupplierDetails(val supplierId: Long) : AppRoute
    @Serializable data class SupplierLedger(val supplierId: Long) : AppRoute

    // Items & Inventory
    @Serializable data object AddProduct : AppRoute
    @Serializable data class EditProduct(val productId: Long) : AppRoute
    @Serializable data class ProductDetails(val productId: Long) : AppRoute
    @Serializable data object Categories : AppRoute
    @Serializable data object Units : AppRoute
    @Serializable data object Inventory : AppRoute
    @Serializable data class StockAdjustment(val productId: Long? = null) : AppRoute
    @Serializable data class StockTransfer(val productId: Long? = null) : AppRoute

    // Purchases
    @Serializable data object Purchases : AppRoute
    @Serializable data class CreatePurchase(val isReturn: Boolean = false) : AppRoute
    @Serializable data class PurchaseDetails(val purchaseId: Long) : AppRoute
    @Serializable data object PurchaseReturns : AppRoute

    // Finance & Double-Entry Accounting
    @Serializable data object Payments : AppRoute
    @Serializable data object Cashbook : AppRoute
    @Serializable data object Expenses : AppRoute
    @Serializable data object Accounting : AppRoute
    @Serializable data object JournalEntries : AppRoute
    @Serializable data object TrialBalance : AppRoute
    @Serializable data object ProfitAndLoss : AppRoute
    @Serializable data object BalanceSheet : AppRoute
    @Serializable data object BankAccounts : AppRoute

    // POS & Reports
    @Serializable data object Pos : AppRoute
    @Serializable data object GstReports : AppRoute
    @Serializable data object BusinessReports : AppRoute

    // Enterprise Modules (More Hub)
    @Serializable data object VoiceAssistant : AppRoute
    @Serializable data object StockPrediction : AppRoute
    @Serializable data object ProfitIntelligence : AppRoute
    @Serializable data object BusinessHealth : AppRoute
    @Serializable data object PaymentReminders : AppRoute
    @Serializable data object PrinterSettings : AppRoute
    @Serializable data object UpiSettings : AppRoute
    @Serializable data object LoyaltyProgram : AppRoute
    @Serializable data object BusinessSwitcher : AppRoute
    @Serializable data object PriceHistory : AppRoute
    @Serializable data object BatchExpiryTracking : AppRoute
    @Serializable data object DailyClosing : AppRoute
    @Serializable data object CsvImportExport : AppRoute
    @Serializable data object UniversalRecovery : AppRoute
    @Serializable data object TemplateDesigner : AppRoute

    // Settings, Backup, Security & Help
    @Serializable data object InvoiceTemplates : AppRoute
    @Serializable data object Backup : AppRoute
    @Serializable data object Restore : AppRoute
    @Serializable data object Security : AppRoute
    @Serializable data object ThemeSelection : AppRoute
    @Serializable data object BusinessProfile : AppRoute
    @Serializable data object AppSettings : AppRoute
    @Serializable data object InteractiveTutorial : AppRoute
    @Serializable data object Help : AppRoute
    @Serializable data object LegalPrivacy : AppRoute
    @Serializable data object About : AppRoute
}
