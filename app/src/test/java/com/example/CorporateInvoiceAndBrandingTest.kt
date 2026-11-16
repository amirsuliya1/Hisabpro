package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.BrandingAssetManager
import com.example.core.PdfGenerator
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.BusinessProfile
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.BackupRepository
import com.example.data.repository.BillingRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.InvoiceLineDraft
import com.example.data.repository.SaveInvoiceRequest
import com.example.core.SignatureStrokePoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CorporateInvoiceAndBrandingTest {

    private lateinit var context: Context
    private lateinit var db: HisabPulseDatabase
    private lateinit var prefs: AppPreferencesRepository
    private lateinit var businessRepo: BusinessRepository
    private lateinit var billingRepo: BillingRepository
    private lateinit var backupRepo: BackupRepository

    private val testDbName = "hisabpulse_corporate_wave_test.db"
    private val migDbName = "hisabpulse_mig_4_5_test.db"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
        context.deleteDatabase(migDbName)
        db = Room.databaseBuilder(context, HisabPulseDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()
        HisabPulseDatabase.ensureSystemSeeded(db)
        prefs = AppPreferencesRepository(context)
        businessRepo = BusinessRepository(db, prefs)
        billingRepo = BillingRepository(db)
        backupRepo = BackupRepository(context, db, prefs)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        context.deleteDatabase(testDbName)
        context.deleteDatabase(migDbName)
    }

    @Test
    fun `corporate wave template is seeded as default selected with switcher support`() = runBlocking {
        val templates = businessRepo.invoiceTemplates.first()
        assertEquals(9, templates.size)
        val corpWave = templates.firstOrNull { it.code == "CORPORATE_WAVE" }
        assertNotNull(corpWave)
        assertTrue(corpWave!!.isSelected)
        assertEquals("#0F2D5C", corpWave.accentColorHex)
        assertEquals("#0284C7", corpWave.secondaryColorHex)

        val classic = templates.first { it.code == "CLASSIC_GST" }
        businessRepo.selectInvoiceTemplate(classic.id)
        val afterSwitch = businessRepo.invoiceTemplates.first()
        assertTrue(afterSwitch.first { it.code == "CLASSIC_GST" }.isSelected)
        assertTrue(!afterSwitch.first { it.code == "CORPORATE_WAVE" }.isSelected)
    }

    @Test
    fun `migration 4 to 5 adds branding and secondaryColor columns preserving existing rows`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS `business_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `businessName` TEXT NOT NULL, `ownerName` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `address` TEXT NOT NULL, `city` TEXT NOT NULL, `state` TEXT NOT NULL, `pincode` TEXT NOT NULL, `gstin` TEXT NOT NULL, `pan` TEXT NOT NULL, `isGstRegistered` INTEGER NOT NULL, `logoUri` TEXT NOT NULL, `signatureLabel` TEXT NOT NULL, `upiId` TEXT NOT NULL, `bankName` TEXT NOT NULL, `accountNumber` TEXT NOT NULL, `ifscCode` TEXT NOT NULL, `invoicePrefix` TEXT NOT NULL, `estimatePrefix` TEXT NOT NULL, `purchasePrefix` TEXT NOT NULL, `nextInvoiceNumber` INTEGER NOT NULL, `nextEstimateNumber` INTEGER NOT NULL, `nextPurchaseNumber` INTEGER NOT NULL, `currencySymbol` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)"""
                        )
                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS `invoice_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `businessId` INTEGER NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `accentColorHex` TEXT NOT NULL, `paperSize` TEXT NOT NULL, `fontScale` REAL NOT NULL, `showLogo` INTEGER NOT NULL, `showGstin` INTEGER NOT NULL, `showSignature` INTEGER NOT NULL, `showQrCode` INTEGER NOT NULL, `showHsn` INTEGER NOT NULL, `showTaxBreakdown` INTEGER NOT NULL, `termsAndConditions` TEXT NOT NULL, `footerMessage` TEXT NOT NULL, `isSelected` INTEGER NOT NULL)"""
                        )
                        db.execSQL(
                            """INSERT INTO `business_profiles` VALUES (1, 'Legacy Shop', 'Owner', '9999999999', 'a@b.com', 'Main Road', 'Pune', 'Maharashtra', '411001', '27AAA', 'AAA', 1, '', 'Authorized Signatory', 'shop@upi', '', '', '', 'INV-', 'EST-', 'PUR-', 1, 1, 1, '₹', 1000)"""
                        )
                        db.execSQL(
                            """INSERT INTO `invoice_templates` VALUES (1, 1, 'MODERN_ORANGE', 'Modern Orange', 'Desc', '#EA580C', 'A4', 1.0, 1, 1, 1, 1, 1, 1, 'Terms', 'Thanks', 1)"""
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val rawDb = helper.writableDatabase
        HisabPulseDatabase.MIGRATION_4_5.migrate(rawDb)

        val bizCursor = rawDb.query("SELECT businessName, tagline, website, signatureUri, sealUri FROM business_profiles WHERE id = 1")
        assertTrue(bizCursor.moveToFirst())
        assertEquals("Legacy Shop", bizCursor.getString(0))
        assertEquals("", bizCursor.getString(1))
        assertEquals("", bizCursor.getString(2))
        assertEquals("", bizCursor.getString(3))
        assertEquals("", bizCursor.getString(4))
        bizCursor.close()

        val tplCursor = rawDb.query("SELECT code, secondaryColorHex FROM invoice_templates WHERE id = 1")
        assertTrue(tplCursor.moveToFirst())
        assertEquals("MODERN_ORANGE", tplCursor.getString(0))
        assertEquals("#0284C7", tplCursor.getString(1))
        tplCursor.close()

        helper.close()
    }

    @Test
    fun `drawn signature and seal assets render into corporate wave invoice pdf`() = runBlocking {
        val strokes = listOf(
            listOf(
                SignatureStrokePoint(20f, 100f),
                SignatureStrokePoint(120f, 40f),
                SignatureStrokePoint(260f, 110f),
                SignatureStrokePoint(380f, 60f)
            )
        )
        val sigUri = BrandingAssetManager.saveSignatureStrokesToPng(
            context = context,
            strokes = strokes,
            canvasWidth = 400f,
            canvasHeight = 160f,
            strokeColorInt = Color.rgb(15, 45, 92)
        ).getOrThrow()
        assertTrue(sigUri.startsWith("file://"))

        val sealFile = File(context.filesDir, "test_seal.png")
        val sealBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(15, 45, 92))
        }
        FileOutputStream(sealFile).use { out -> sealBmp.compress(Bitmap.CompressFormat.PNG, 95, out) }
        sealBmp.recycle()

        val decodedSig = BrandingAssetManager.decodeDownsampledBitmap(context, sigUri, 320, 160)
        val decodedSeal = BrandingAssetManager.decodeDownsampledBitmap(context, sealFile.absolutePath, 200, 200)
        assertNotNull(decodedSig)
        assertNotNull(decodedSeal)

        val customProfile = BusinessProfile(
            businessName = "Arlsan Enterprises",
            tagline = "Precision Wholesale & Distribution",
            phone = "+91 98765 43210",
            email = "billing@arlsan.example",
            website = "www.arlsan.example",
            address = "42 Market Plaza",
            city = "Mumbai",
            state = "Maharashtra",
            gstin = "27AABCU9603R1ZM",
            upiId = "arlsan@upi",
            signatureLabel = "Authorized Signature",
            signatureUri = sigUri,
            sealUri = sealFile.absolutePath
        )
        businessRepo.saveBusinessProfile(customProfile)

        val invId = billingRepo.saveOrFinalizeInvoice(
            SaveInvoiceRequest(
                customerName = "Metro Mart",
                customerGstin = "27METRO1234A1Z5",
                paidAmount = BigDecimal("500"),
                items = listOf(
                    InvoiceLineDraft(
                        productName = "Corporate Stationery Kit",
                        quantity = BigDecimal("2"),
                        rate = BigDecimal("350"),
                        taxRatePercent = BigDecimal("18")
                    )
                )
            )
        ).getOrThrow()
        val (invoice, items) = billingRepo.getInvoiceWithItemsOnce(invId)!!
        val corpTemplate = businessRepo.invoiceTemplates.first()
            .first { it.code == "CORPORATE_WAVE" }
            .copy(accentColorHex = "#1E3A8A", secondaryColorHex = "#0EA5E9")

        val pdfFile = PdfGenerator.generateInvoicePdf(context, customProfile, invoice, items, corpTemplate)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0L)
        assertEquals("%PDF-", pdfFile.readBytes().copyOfRange(0, 5).toString(Charsets.US_ASCII))
    }

    @Test
    fun `v5 backup snapshot roundtrips custom branding and secondary template color`() = runBlocking {
        val profile = businessRepo.businessProfile.first() ?: BusinessProfile(businessName = "Test")
        businessRepo.saveBusinessProfile(
            profile.copy(
                businessName = "Royal Pulse Store",
                tagline = "Best Quality Always",
                website = "www.royalpulse.example",
                signatureUri = "/tmp/sig_test.png",
                sealUri = "/tmp/seal_test.png"
            )
        )
        val corp = businessRepo.invoiceTemplates.first().first { it.code == "CORPORATE_WAVE" }
        businessRepo.updateInvoiceTemplate(corp.copy(accentColorHex = "#0F766E", secondaryColorHex = "#14B8A6"))

        val bytes = backupRepo.createBackupBytes(encrypt = true, passphrase = "WaveKey#5")
        val snap = backupRepo.decodeAndValidateBackupBytes(bytes, "WaveKey#5")
        assertEquals(5, snap.schemaVersion)
        assertEquals("Best Quality Always", snap.businessProfiles.first().tagline)
        assertEquals("www.royalpulse.example", snap.businessProfiles.first().website)
        assertEquals("/tmp/sig_test.png", snap.businessProfiles.first().signatureUri)
        assertEquals("/tmp/seal_test.png", snap.businessProfiles.first().sealUri)
        assertEquals("#14B8A6", snap.invoiceTemplates.first { it.code == "CORPORATE_WAVE" }.secondaryColorHex)

        val restoreRes = backupRepo.restoreFromSnapshot(snap)
        assertTrue(restoreRes.isSuccess)
        val restoredBiz = businessRepo.businessProfile.first()!!
        assertEquals("Best Quality Always", restoredBiz.tagline)
        assertEquals("www.royalpulse.example", restoredBiz.website)
        val restoredTpl = businessRepo.invoiceTemplates.first().first { it.code == "CORPORATE_WAVE" }
        assertEquals("#0F766E", restoredTpl.accentColorHex)
        assertEquals("#14B8A6", restoredTpl.secondaryColorHex)
    }
}
