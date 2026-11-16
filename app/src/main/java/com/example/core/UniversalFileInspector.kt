package com.example.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

enum class InspectedFileFormat(val label: String) {
    HISABP_ENCRYPTED("Encrypted HisabPulse Backup (.bizmgr / .hpbk)"),
    JSON_BACKUP("JSON Snapshot / Data"),
    XLSX_SPREADSHEET("Excel Spreadsheet (.xlsx)"),
    CSV_SPREADSHEET("CSV Spreadsheet (.csv)"),
    SQLITE_DATABASE("SQLite Database (.db)"),
    SQL_DUMP("SQL Script Dump (.sql)"),
    XML_DATA("XML Data File (.xml)"),
    PDF_DOCUMENT("PDF Document (.pdf)"),
    ZIP_ARCHIVE("ZIP Data Archive (.zip)"),
    VYP_CONTAINER("Vyapar File Container (.vyp)"),
    IMAGE_ATTACHMENT("Image Attachment"),
    UNSUPPORTED("Unknown or Unsupported Binary File")
}

data class FileInspectionReport(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val detectedFormat: InspectedFileFormat,
    val isEncrypted: Boolean = false,
    val canDirectlyRecover: Boolean = false,
    val summaryMessage: String,
    val detectedEntries: List<String> = emptyList(),
    val honestLimitationNote: String = ""
)

object UniversalFileInspector {
    private const val MAX_ZIP_ENTRIES = 500
    private const val MAX_ZIP_UNCOMPRESSED_BYTES = 25 * 1024 * 1024

    fun inspect(fileName: String, bytes: ByteArray): FileInspectionReport {
        val sha = SecurityManager.sha256Hex(bytes)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val size = bytes.size.toLong()

        if (bytes.isEmpty()) {
            return FileInspectionReport(
                fileName = fileName,
                sizeBytes = 0L,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.UNSUPPORTED,
                summaryMessage = "File is empty (0 bytes)."
            )
        }

        // 1. Magic byte: Encrypted HPBK (.bizmgr or .hpbk)
        if (SecurityManager.isEncryptedBackup(bytes)) {
            return FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.HISABP_ENCRYPTED,
                isEncrypted = true,
                canDirectlyRecover = true,
                summaryMessage = "AES-256-GCM encrypted HisabPulse backup container detected. Ready for integrity check & restore."
            )
        }

        // 2. Magic byte: PDF
        if (bytes.size >= 5 && String(bytes.copyOfRange(0, 5), Charsets.US_ASCII) == "%PDF-") {
            return FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.PDF_DOCUMENT,
                canDirectlyRecover = false,
                summaryMessage = "PDF document detected (${size / 1024} KB). PDFs can be archived as attachments; structured ledger recovery requires JSON, CSV, XLSX, or encrypted backup files.",
                honestLimitationNote = "PDF files do not store structured database schemas for lossless accounting restoration."
            )
        }

        // 3. Magic byte: SQLite 3
        if (bytes.size >= 16 && String(bytes.copyOfRange(0, 15), Charsets.US_ASCII) == "SQLite format 3") {
            return FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.SQLITE_DATABASE,
                canDirectlyRecover = true,
                summaryMessage = "SQLite 3 database header detected. Read-only table inspection available to recover compatible customers, suppliers, products, and invoices."
            )
        }

        // 4. Magic byte: Image attachments (PNG / JPEG / WEBP)
        val isPng = bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()
        val isJpg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        if (isPng || isJpg || ext in listOf("png", "jpg", "jpeg", "webp")) {
            return FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.IMAGE_ATTACHMENT,
                canDirectlyRecover = false,
                summaryMessage = "Image receipt/attachment detected (${size / 1024} KB)."
            )
        }

        // 5. Magic byte: ZIP container (PK\x03\x04) -> XLSX, ZIP backup, or VYP ZIP
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            val zipResult = inspectZipEntries(bytes)
            if (zipResult.isFailure) {
                return FileInspectionReport(
                    fileName = fileName,
                    sizeBytes = size,
                    sha256 = sha,
                    detectedFormat = if (ext == "vyp") InspectedFileFormat.VYP_CONTAINER else InspectedFileFormat.ZIP_ARCHIVE,
                    isEncrypted = true,
                    canDirectlyRecover = false,
                    summaryMessage = "Archive inspection failed: ${zipResult.exceptionOrNull()?.message}",
                    honestLimitationNote = "Encrypted or malformed archives cannot be bypassed."
                )
            }
            val names = zipResult.getOrThrow()
            return when {
                names.any { it == "[Content_Types].xml" } || names.any { it.startsWith("xl/worksheets/") } ->
                    FileInspectionReport(
                        fileName = fileName,
                        sizeBytes = size,
                        sha256 = sha,
                        detectedFormat = InspectedFileFormat.XLSX_SPREADSHEET,
                        canDirectlyRecover = true,
                        summaryMessage = "Valid OpenXML Excel (.xlsx) workbook detected with ${names.size} archive entries.",
                        detectedEntries = names
                    )
                ext == "vyp" -> {
                    val readable = names.filter {
                        val lower = it.lowercase()
                        lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".db") ||
                            lower.endsWith(".sqlite") || lower.endsWith(".xml")
                    }
                    FileInspectionReport(
                        fileName = fileName,
                        sizeBytes = size,
                        sha256 = sha,
                        detectedFormat = InspectedFileFormat.VYP_CONTAINER,
                        canDirectlyRecover = readable.isNotEmpty(),
                        detectedEntries = names,
                        summaryMessage = if (readable.isNotEmpty()) {
                            "Vyapar (.vyp) ZIP container with ${readable.size} readable data entries (${readable.take(3).joinToString()})."
                        } else {
                            "Vyapar (.vyp) container detected (${names.size} entries), but no open readable JSON/CSV/SQLite tables were exposed."
                        },
                        honestLimitationNote = if (readable.isEmpty()) {
                            "Proprietary or encrypted .vyp entries cannot be decoded reliably. Export CSV or Excel (.xlsx) from Vyapar and use Guided CSV/XLSX Import."
                        } else ""
                    )
                }
                else -> FileInspectionReport(
                    fileName = fileName,
                    sizeBytes = size,
                    sha256 = sha,
                    detectedFormat = InspectedFileFormat.ZIP_ARCHIVE,
                    canDirectlyRecover = names.any {
                        it.endsWith(".json", true) || it.endsWith(".csv", true) ||
                            it.endsWith(".xlsx", true) || it.endsWith(".bizmgr", true) || it.endsWith(".hpbk", true)
                    },
                    detectedEntries = names,
                    summaryMessage = "ZIP archive with ${names.size} entries detected."
                )
            }
        }

        // 6. Explicitly handle non-ZIP .vyp binary/encrypted containers honestly
        if (ext == "vyp") {
            return FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.VYP_CONTAINER,
                isEncrypted = true,
                canDirectlyRecover = false,
                summaryMessage = "Vyapar (.vyp) binary/encrypted backup detected (${size / 1024} KB). Direct binary decoding is not supported without vendor specification.",
                honestLimitationNote = "To protect data accuracy, HisabPulse never guesses encrypted binary .vyp files. Please export Parties, Items, or Transactions as CSV/Excel (.xlsx) from Vyapar and import safely."
            )
        }

        // 7. Text-based inspection (JSON, XML, SQL, CSV)
        val previewBytes = bytes.copyOfRange(0, minOf(bytes.size, 8192))
        val nonPrintable = previewBytes.count { b ->
            val u = b.toInt() and 0xFF
            u < 9 || (u in 14..31)
        }
        if (nonPrintable > previewBytes.size / 8) {
            return FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.UNSUPPORTED,
                canDirectlyRecover = false,
                summaryMessage = "Unrecognized binary file ($ext, ${size / 1024} KB). Recovery skipped to prevent data corruption."
            )
        }

        val text = String(bytes, Charsets.UTF_8).trimStart()
        return when {
            text.startsWith("{") || text.startsWith("[") -> FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.JSON_BACKUP,
                canDirectlyRecover = true,
                summaryMessage = "Structured JSON data detected (${size / 1024} KB)."
            )
            text.startsWith("<") -> FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.XML_DATA,
                canDirectlyRecover = true,
                summaryMessage = "XML data file detected (${size / 1024} KB)."
            )
            text.contains("CREATE TABLE", ignoreCase = true) || text.contains("INSERT INTO", ignoreCase = true) ->
                FileInspectionReport(
                    fileName = fileName,
                    sizeBytes = size,
                    sha256 = sha,
                    detectedFormat = InspectedFileFormat.SQL_DUMP,
                    canDirectlyRecover = true,
                    summaryMessage = "SQL dump script detected (${size / 1024} KB)."
                )
            text.contains(",") && text.contains("\n") || ext == "csv" ->
                FileInspectionReport(
                    fileName = fileName,
                    sizeBytes = size,
                    sha256 = sha,
                    detectedFormat = InspectedFileFormat.CSV_SPREADSHEET,
                    canDirectlyRecover = true,
                    summaryMessage = "CSV spreadsheet detected (${text.lines().size} lines)."
                )
            else -> FileInspectionReport(
                fileName = fileName,
                sizeBytes = size,
                sha256 = sha,
                detectedFormat = InspectedFileFormat.UNSUPPORTED,
                canDirectlyRecover = false,
                summaryMessage = "Text format not recognized as valid JSON, CSV, XML, or SQL."
            )
        }
    }

    fun extractZipFiles(bytes: ByteArray): Result<Map<String, ByteArray>> = runCatching {
        val map = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            var count = 0
            var totalBytes = 0
            while (entry != null) {
                count++
                require(count <= MAX_ZIP_ENTRIES) { "ZIP archive exceeds safe entry limit ($MAX_ZIP_ENTRIES)" }
                val name = entry.name
                require(!name.contains("..")) { "Zip-slip path traversal rejected: $name" }
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val read = zis.read(buf)
                        if (read <= 0) break
                        totalBytes += read
                        require(totalBytes <= MAX_ZIP_UNCOMPRESSED_BYTES) {
                            "Uncompressed archive exceeds safe 25 MB limit"
                        }
                        out.write(buf, 0, read)
                    }
                    map[name] = out.toByteArray()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        map
    }

    private fun inspectZipEntries(bytes: ByteArray): Result<List<String>> =
        extractZipFiles(bytes).map { it.keys.toList() }
}
