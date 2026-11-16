package com.example.data.local.entities

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode(val label: String) {
    LIGHT("Clean Light"),
    DARK("Charcoal Dark"),
    ORANGE("Orange Brand")
}

@Serializable
enum class BusinessCategory(val label: String) {
    RETAIL("Retail Shop"),
    WHOLESALE("Wholesale"),
    SUPERMARKET("Supermarket"),
    MEDICAL("Medical Store"),
    CLOTHING("Clothing & Garments"),
    ELECTRONICS("Electronics & Mobile"),
    HARDWARE("Hardware Store"),
    RESTAURANT("Restaurant & Cafe"),
    DISTRIBUTOR("Distributor"),
    MANUFACTURER("Manufacturer"),
    SERVICE("Service Provider"),
    GENERAL_TRADING("General Trading")
}

@Serializable
enum class InvoiceDocumentType(val label: String, val affectsStock: Boolean, val affectsLedger: Boolean) {
    TAX_INVOICE("GST Tax Invoice", true, true),
    NON_GST_INVOICE("Non-GST Invoice", true, true),
    RETAIL_INVOICE("Retail Invoice", true, true),
    WHOLESALE_INVOICE("Wholesale Invoice", true, true),
    ESTIMATE("Estimate", false, false),
    QUOTATION("Quotation", false, false),
    PROFORMA("Proforma Invoice", false, false),
    DELIVERY_CHALLAN("Delivery Challan", true, false),
    CREDIT_NOTE("Credit Note", false, true),
    DEBIT_NOTE("Debit Note", false, true),
    SALES_RETURN("Sales Return", true, true)
}

@Serializable
enum class InvoiceStatus(val label: String) {
    DRAFT("Draft"),
    FINALIZED("Unpaid"),
    PARTIAL("Partially Paid"),
    PAID("Paid"),
    CANCELLED("Cancelled"),
    HELD_POS("Held POS")
}

@Serializable
enum class PurchaseDocumentType(val label: String) {
    PURCHASE_INVOICE("Purchase Invoice"),
    PURCHASE_RETURN("Purchase Return"),
    DEBIT_NOTE("Supplier Debit Note")
}

@Serializable
enum class PurchaseStatus(val label: String) {
    DRAFT("Draft"),
    FINALIZED("Unpaid"),
    PARTIAL("Partially Paid"),
    PAID("Paid"),
    CANCELLED("Cancelled")
}

@Serializable
enum class PaymentMode(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    BANK_TRANSFER("Bank Transfer"),
    CARD("Card"),
    CHEQUE("Cheque"),
    OTHER("Other")
}

@Serializable
enum class PaymentDirection(val label: String) {
    RECEIVED("Received"),
    PAID("Paid"),
    CUSTOMER_ADVANCE("Customer Advance"),
    SUPPLIER_ADVANCE("Supplier Advance"),
    REFUND_GIVEN("Refund Given"),
    REFUND_RECEIVED("Refund Received")
}

@Serializable
enum class StockMovementType(val label: String) {
    OPENING("Opening Stock"),
    SALE_OUT("Sale"),
    PURCHASE_IN("Purchase"),
    SALES_RETURN_IN("Sales Return"),
    PURCHASE_RETURN_OUT("Purchase Return"),
    ADJUSTMENT_IN("Stock Adjustment (+)"),
    ADJUSTMENT_OUT("Stock Adjustment (-)"),
    TRANSFER_OUT("Transfer Out"),
    TRANSFER_IN("Transfer In"),
    PRODUCTION_CONSUME("Raw Material Consumed"),
    PRODUCTION_OUTPUT("Finished Goods Produced"),
    REVERSAL_IN("Invoice Cancellation Reversal")
}

@Serializable
enum class AccountType(val label: String) {
    ASSET("Asset"),
    LIABILITY("Liability"),
    INCOME("Income"),
    EXPENSE("Expense"),
    EQUITY("Equity")
}

@Serializable
enum class LoyaltyTier(val label: String, val minPoints: Long, val multiplierScaled: Long) {
    SILVER("Silver", 0L, 100L),
    GOLD("Gold", 500L, 125L),
    PLATINUM("Platinum", 2000L, 150L)
}

@Serializable
enum class LoyaltyTxnType(val label: String) {
    EARNED("Earned"),
    REDEEMED("Redeemed"),
    EXPIRED("Expired"),
    ADJUSTED("Adjusted"),
    REVERSED("Reversed")
}

@Serializable
enum class ReminderChannel(val label: String) {
    WHATSAPP("WhatsApp"),
    SMS("SMS"),
    COPY("Copied"),
    MANUAL("Marked Sent")
}

@Serializable
enum class RecoveryMode(val label: String) {
    FULL_RESTORE("Full Restore"),
    MERGE_IMPORT("Merge Import"),
    SELECTIVE_RECOVERY("Selective Recovery"),
    NEW_BUSINESS_IMPORT("New Business Import"),
    MIGRATION_IMPORT("Migration Import"),
    REPAIR_VALIDATE("Repair & Validate")
}

@Serializable
enum class DuplicateStrategy(val label: String) {
    SKIP("Skip Duplicates"),
    UPDATE("Update Existing"),
    MANUAL_REVIEW("Manual Review")
}

@Serializable
enum class ThermalPaperWidth(val label: String, val charsPerLine: Int) {
    MM_58("58mm (32 cols)", 32),
    MM_80("80mm (48 cols)", 48)
}

@Serializable
enum class InvoicePaperSize(val label: String) {
    A4("A4 Full Page"),
    A5("A5 Half Page"),
    THERMAL_58("58mm Thermal"),
    THERMAL_80("80mm Thermal")
}

@Serializable
enum class SearchModule(val label: String) {
    INVENTORY("Inventory"),
    INVOICE("Invoice")
}

