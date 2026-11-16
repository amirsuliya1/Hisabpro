package com.example.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Customer
import com.example.data.local.entities.DailyClosing
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.LedgerEntry
import com.example.data.local.entities.Payment
import com.example.data.local.entities.PrinterConfiguration
import com.example.data.local.entities.ThermalPaperWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

data class PairedPrinterDevice(
    val name: String,
    val macAddress: String
)

object EscPosPrinterManager {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun listPairedDevices(context: Context): Result<List<PairedPrinterDevice>> = runCatching {
        if (!hasBluetoothConnectPermission(context)) {
            error("Bluetooth permission is required to scan paired printers")
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter = manager?.adapter ?: error("Bluetooth adapter unavailable on this device")
        if (!adapter.isEnabled) {
            error("Turn on Bluetooth to discover paired thermal printers")
        }
        adapter.bondedDevices.orEmpty().map { dev ->
            PairedPrinterDevice(
                name = dev.name ?: "Bluetooth Printer",
                macAddress = dev.address ?: ""
            )
        }.sortedBy { it.name }
    }

    fun buildInvoiceReceiptText(
        profile: BusinessProfile?,
        invoice: Invoice,
        items: List<InvoiceItem>,
        config: PrinterConfiguration = PrinterConfiguration()
    ): String {
        val cols = config.paperWidth.charsPerLine
        val sep = "-".repeat(cols)
        val currency = "Rs "
        val sb = StringBuilder()

        fun center(text: String): String {
            val clean = text.take(cols)
            val pad = ((cols - clean.length) / 2).coerceAtLeast(0)
            return " ".repeat(pad) + clean
        }

        fun leftRight(left: String, right: String): String {
            val r = right.take(cols / 2)
            val maxL = (cols - r.length - 1).coerceAtLeast(4)
            val l = left.take(maxL)
            val spaces = (cols - l.length - r.length).coerceAtLeast(1)
            return l + " ".repeat(spaces) + r
        }

        if (config.showBusinessName) {
            sb.appendLine(center(profile?.businessName ?: "HISABPULSE STORE"))
        }
        if (config.showAddress && !profile?.address.isNullOrBlank()) {
            sb.appendLine(center(profile!!.address))
        }
        if (config.showPhone && !profile?.phone.isNullOrBlank()) {
            sb.appendLine(center("Ph: ${profile!!.phone}"))
        }
        if (config.showGstin && !profile?.gstin.isNullOrBlank()) {
            sb.appendLine(center("GSTIN: ${profile!!.gstin}"))
        }
        sb.appendLine(sep)
        sb.appendLine(center(invoice.documentType.label.uppercase()))
        if (config.showInvoiceNumber) {
            sb.appendLine(leftRight("No: ${invoice.invoiceNumber}", invoice.status.label))
        }
        if (config.showDateTime) {
            sb.appendLine("Date: ${DateUtils.formatDateTime(invoice.invoiceDateMillis)}")
        }
        sb.appendLine("Party: ${invoice.customerName.take(cols - 7)}")
        sb.appendLine(sep)

        if (config.showItemTable) {
            sb.appendLine(leftRight("ITEM / QTY x RATE", "AMT"))
            sb.appendLine(sep)
            for (item in items) {
                sb.appendLine(item.productName.take(cols))
                val qty = Money.formatQty(Money.fromPaise(item.quantityScaled))
                val rate = Money.formatPlain(Money.fromPaise(item.ratePaise))
                val amt = Money.formatPlain(Money.fromPaise(item.lineTotalPaise))
                sb.appendLine(leftRight("  $qty ${item.unitName} x $rate", amt))
            }
            sb.appendLine(sep)
        }

        sb.appendLine(leftRight("Subtotal", currency + Money.formatPlain(Money.fromPaise(invoice.subtotalPaise))))
        val totalDisc = invoice.itemDiscountPaise + invoice.invoiceDiscountFlatPaise
        if (config.showDiscount && totalDisc > 0L) {
            sb.appendLine(leftRight("Discount", "-" + currency + Money.formatPlain(Money.fromPaise(totalDisc))))
        }
        if (config.showTax && invoice.totalTaxPaise > 0L) {
            if (invoice.cgstPaise > 0L) {
                sb.appendLine(leftRight("CGST", currency + Money.formatPlain(Money.fromPaise(invoice.cgstPaise))))
                sb.appendLine(leftRight("SGST", currency + Money.formatPlain(Money.fromPaise(invoice.sgstPaise))))
            }
            if (invoice.igstPaise > 0L) {
                sb.appendLine(leftRight("IGST", currency + Money.formatPlain(Money.fromPaise(invoice.igstPaise))))
            }
        }
        sb.appendLine(sep)
        sb.appendLine(leftRight("GRAND TOTAL", currency + Money.formatPlain(Money.fromPaise(invoice.grandTotalPaise))))
        if (config.showPaidAndDue) {
            sb.appendLine(leftRight("Paid (${invoice.paymentMode.label})", currency + Money.formatPlain(Money.fromPaise(invoice.paidAmountPaise))))
            sb.appendLine(leftRight("Balance Due", currency + Money.formatPlain(Money.fromPaise(invoice.balanceDuePaise))))
        }
        if (config.showPaymentQr && !profile?.upiId.isNullOrBlank()) {
            sb.appendLine(sep)
            sb.appendLine(center("UPI: ${profile!!.upiId}"))
        }
        sb.appendLine(sep)
        if (config.footerMessage.isNotBlank()) {
            sb.appendLine(center(config.footerMessage))
        }
        return sb.toString()
    }

    fun buildPaymentReceiptText(
        profile: BusinessProfile?,
        payment: Payment,
        width: ThermalPaperWidth = ThermalPaperWidth.MM_58
    ): String {
        val cols = width.charsPerLine
        val sep = "-".repeat(cols)
        return buildString {
            appendLine((profile?.businessName ?: "HISABPULSE").take(cols))
            appendLine("PAYMENT RECEIPT")
            appendLine(sep)
            appendLine("Receipt: ${payment.receiptNumber}")
            appendLine("Date: ${DateUtils.formatDateTime(payment.paymentDateMillis)}")
            appendLine("Party: ${payment.partyName}")
            appendLine("Mode: ${payment.paymentMode.label}")
            if (payment.referenceNumber.isNotBlank()) appendLine("Ref: ${payment.referenceNumber}")
            appendLine("Amount: Rs ${Money.formatPlain(Money.fromPaise(payment.amountPaise))}")
            appendLine(sep)
        }
    }

    fun buildDailyClosingReceiptText(
        profile: BusinessProfile?,
        closing: DailyClosing,
        width: ThermalPaperWidth = ThermalPaperWidth.MM_58
    ): String {
        val cols = width.charsPerLine
        val sep = "-".repeat(cols)
        return buildString {
            appendLine((profile?.businessName ?: "HISABPULSE").take(cols))
            appendLine("DAILY CLOSING (${closing.closingDateIso})")
            appendLine(sep)
            appendLine("Opening Cash : Rs ${Money.formatPlain(Money.fromPaise(closing.openingCashPaise))}")
            appendLine("Cash Sales   : Rs ${Money.formatPlain(Money.fromPaise(closing.cashSalesPaise))}")
            appendLine("Cash Receipts: Rs ${Money.formatPlain(Money.fromPaise(closing.cashReceiptsPaise))}")
            appendLine("Cash Expenses: Rs ${Money.formatPlain(Money.fromPaise(closing.cashExpensesPaise))}")
            appendLine("Expected Cash: Rs ${Money.formatPlain(Money.fromPaise(closing.expectedClosingCashPaise))}")
            appendLine("Counted Cash : Rs ${Money.formatPlain(Money.fromPaise(closing.actualCountedCashPaise))}")
            appendLine("Difference   : Rs ${Money.formatPlain(Money.fromPaise(closing.cashDifferencePaise))}")
            appendLine(sep)
            appendLine("UPI Coll.    : Rs ${Money.formatPlain(Money.fromPaise(closing.upiCollectionsPaise))}")
            appendLine("Bank Coll.   : Rs ${Money.formatPlain(Money.fromPaise(closing.bankCollectionsPaise))}")
            appendLine("Credit Sales : Rs ${Money.formatPlain(Money.fromPaise(closing.creditSalesPaise))}")
            appendLine("Total Sales  : Rs ${Money.formatPlain(Money.fromPaise(closing.totalSalesPaise))}")
            appendLine(sep)
        }
    }

    fun buildCustomerLedgerText(
        profile: BusinessProfile?,
        customer: Customer,
        entries: List<LedgerEntry>,
        width: ThermalPaperWidth = ThermalPaperWidth.MM_58
    ): String {
        val cols = width.charsPerLine
        val sep = "-".repeat(cols)
        return buildString {
            appendLine((profile?.businessName ?: "HISABPULSE").take(cols))
            appendLine("LEDGER: ${customer.name}")
            appendLine("Balance: Rs ${Money.formatPlain(Money.fromPaise(customer.currentBalancePaise))}")
            appendLine(sep)
            entries.takeLast(15).forEach { e ->
                val d = DateUtils.formatDate(e.entryDateMillis)
                val amt = if (e.debitPaise > 0L) "+${Money.formatPlain(Money.fromPaise(e.debitPaise))}"
                else "-${Money.formatPlain(Money.fromPaise(e.creditPaise))}"
                appendLine("$d | $amt")
                appendLine("  ${e.narration.take(cols - 2)}")
            }
            appendLine(sep)
        }
    }

    fun buildEscPosBytes(
        receiptText: String,
        upiQrPayload: String? = null,
        includeCutCommand: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // ESC @ - Initialize printer
        out.write(byteArrayOf(0x1B, 0x40))
        // Left align default
        out.write(byteArrayOf(0x1B, 0x61, 0x00))
        val cleanText = receiptText.replace("₹", "Rs ")
        out.write(cleanText.toByteArray(Charsets.US_ASCII))
        out.write("\n".toByteArray(Charsets.US_ASCII))

        if (!upiQrPayload.isNullOrBlank()) {
            // Center align QR
            out.write(byteArrayOf(0x1B, 0x61, 0x01))
            val qrBytes = upiQrPayload.toByteArray(Charsets.UTF_8)
            val len = qrBytes.size + 3
            val pL = (len and 0xFF).toByte()
            val pH = ((len shr 8) and 0xFF).toByte()
            // GS ( k: select model 2, size 5, EC L, store data, print QR
            out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
            out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x05))
            out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))
            out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
            out.write(qrBytes)
            out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
            out.write("\nScan UPI to Pay\n".toByteArray(Charsets.US_ASCII))
        }

        out.write("\n\n\n".toByteArray(Charsets.US_ASCII))
        if (includeCutCommand) {
            // GS V A 0 - partial cut with feed
            out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        }
        return out.toByteArray()
    }

    @SuppressLint("MissingPermission")
    suspend fun sendToBluetoothPrinter(
        context: Context,
        macAddress: String,
        escPosBytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(macAddress.isNotBlank()) { "Configure a Bluetooth printer MAC address first" }
            if (!hasBluetoothConnectPermission(context)) {
                error("Bluetooth Connect permission not granted")
            }
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: error("Bluetooth is not supported on this device")
            require(adapter.isEnabled) { "Bluetooth is turned off" }

            val device = adapter.getRemoteDevice(macAddress)
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                socket.connect()
                socket.outputStream.write(escPosBytes)
                socket.outputStream.flush()
            } finally {
                runCatching { socket.close() }
            }
            Unit
        }
    }
}
