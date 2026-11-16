package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.local.entities.AccountType
import com.example.data.local.entities.AppThemeMode
import com.example.data.local.entities.BusinessCategory
import com.example.data.local.entities.DuplicateStrategy
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoicePaperSize
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.LoyaltyTier
import com.example.data.local.entities.LoyaltyTxnType
import com.example.data.local.entities.PaymentDirection
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.PurchaseDocumentType
import com.example.data.local.entities.PurchaseStatus
import com.example.data.local.entities.RecoveryMode
import com.example.data.local.entities.ReminderChannel
import com.example.data.local.entities.SearchModule
import com.example.data.local.entities.StockMovementType
import com.example.data.local.entities.ThermalPaperWidth

class HisabPulseConverters {
    @TypeConverter fun fromThemeMode(v: AppThemeMode): String = v.name
    @TypeConverter fun toThemeMode(v: String): AppThemeMode = AppThemeMode.valueOf(v)

    @TypeConverter fun fromBusinessCategory(v: BusinessCategory): String = v.name
    @TypeConverter fun toBusinessCategory(v: String): BusinessCategory = BusinessCategory.valueOf(v)

    @TypeConverter fun fromInvoiceDocType(v: InvoiceDocumentType): String = v.name
    @TypeConverter fun toInvoiceDocType(v: String): InvoiceDocumentType = InvoiceDocumentType.valueOf(v)

    @TypeConverter fun fromInvoiceStatus(v: InvoiceStatus): String = v.name
    @TypeConverter fun toInvoiceStatus(v: String): InvoiceStatus = InvoiceStatus.valueOf(v)

    @TypeConverter fun fromPurchaseDocType(v: PurchaseDocumentType): String = v.name
    @TypeConverter fun toPurchaseDocType(v: String): PurchaseDocumentType = PurchaseDocumentType.valueOf(v)

    @TypeConverter fun fromPurchaseStatus(v: PurchaseStatus): String = v.name
    @TypeConverter fun toPurchaseStatus(v: String): PurchaseStatus = PurchaseStatus.valueOf(v)

    @TypeConverter fun fromPaymentMode(v: PaymentMode): String = v.name
    @TypeConverter fun toPaymentMode(v: String): PaymentMode = PaymentMode.valueOf(v)

    @TypeConverter fun fromPaymentDirection(v: PaymentDirection): String = v.name
    @TypeConverter fun toPaymentDirection(v: String): PaymentDirection = PaymentDirection.valueOf(v)

    @TypeConverter fun fromStockMovementType(v: StockMovementType): String = v.name
    @TypeConverter fun toStockMovementType(v: String): StockMovementType = StockMovementType.valueOf(v)

    @TypeConverter fun fromAccountType(v: AccountType): String = v.name
    @TypeConverter fun toAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun fromLoyaltyTier(v: LoyaltyTier): String = v.name
    @TypeConverter fun toLoyaltyTier(v: String): LoyaltyTier = LoyaltyTier.valueOf(v)

    @TypeConverter fun fromLoyaltyTxnType(v: LoyaltyTxnType): String = v.name
    @TypeConverter fun toLoyaltyTxnType(v: String): LoyaltyTxnType = LoyaltyTxnType.valueOf(v)

    @TypeConverter fun fromReminderChannel(v: ReminderChannel): String = v.name
    @TypeConverter fun toReminderChannel(v: String): ReminderChannel = ReminderChannel.valueOf(v)

    @TypeConverter fun fromRecoveryMode(v: RecoveryMode): String = v.name
    @TypeConverter fun toRecoveryMode(v: String): RecoveryMode = RecoveryMode.valueOf(v)

    @TypeConverter fun fromDuplicateStrategy(v: DuplicateStrategy): String = v.name
    @TypeConverter fun toDuplicateStrategy(v: String): DuplicateStrategy = DuplicateStrategy.valueOf(v)

    @TypeConverter fun fromThermalPaperWidth(v: ThermalPaperWidth): String = v.name
    @TypeConverter fun toThermalPaperWidth(v: String): ThermalPaperWidth = ThermalPaperWidth.valueOf(v)

    @TypeConverter fun fromInvoicePaperSize(v: InvoicePaperSize): String = v.name
    @TypeConverter fun toInvoicePaperSize(v: String): InvoicePaperSize = InvoicePaperSize.valueOf(v)

    @TypeConverter fun fromSearchModule(v: SearchModule): String = v.name
    @TypeConverter fun toSearchModule(v: String): SearchModule = SearchModule.valueOf(v)
}
