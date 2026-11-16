package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.SearchModule
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.repository.BackupRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SearchHistoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchHistoryTest {

    private lateinit var context: Context
    private lateinit var db: HisabPulseDatabase
    private lateinit var prefs: AppPreferencesRepository
    private lateinit var businessRepo: BusinessRepository
    private lateinit var backupRepo: BackupRepository
    private lateinit var searchRepo: SearchHistoryRepository

    private val testDbName = "hisabpulse_search_history_test.db"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
        db = Room.databaseBuilder(context, HisabPulseDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()
        HisabPulseDatabase.ensureSystemSeeded(db)
        prefs = AppPreferencesRepository(context)
        businessRepo = BusinessRepository(db, prefs)
        backupRepo = BackupRepository(context, db, prefs)
        searchRepo = SearchHistoryRepository(db)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        context.deleteDatabase(testDbName)
        context.deleteDatabase("hisabpulse_mig_3_4_unit.db")
    }

    @Test
    fun `ignores short queries trims whitespace and isolates inventory vs invoice modules`() = runBlocking {
        searchRepo.recordSearch(SearchModule.INVENTORY, "   ")
        searchRepo.recordSearch(SearchModule.INVENTORY, "a")
        assertTrue(searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY).isEmpty())

        searchRepo.recordSearch(SearchModule.INVENTORY, "  Rice 5kg  ")
        searchRepo.recordSearch(SearchModule.INVOICE, "INV-1001")

        val invHistory = searchRepo.observeRecentSearches(SearchModule.INVENTORY).first()
        val billHistory = searchRepo.observeRecentSearches(SearchModule.INVOICE).first()

        assertEquals(listOf("Rice 5kg"), invHistory.map { it.query })
        assertEquals(listOf("INV-1001"), billHistory.map { it.query })
    }

    @Test
    fun `case-insensitive deduplication moves re-searched query to top`() = runBlocking {
        searchRepo.recordSearch(SearchModule.INVENTORY, "Sugar")
        delay(5L)
        searchRepo.recordSearch(SearchModule.INVENTORY, "Cooking Oil")
        delay(5L)
        searchRepo.recordSearch(SearchModule.INVENTORY, "sugar")

        val history = searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY)
        assertEquals(2, history.size)
        assertEquals(listOf("sugar", "Cooking Oil"), history.map { it.query })
    }

    @Test
    fun `retains at most maxHistory recent entries per module`() = runBlocking {
        for (i in 1..13) {
            searchRepo.recordSearch(SearchModule.INVOICE, "Customer $i", maxHistory = 10)
            delay(5L)
        }

        val history = searchRepo.getRecentSearchesOnce(SearchModule.INVOICE, limit = 20)
        assertEquals(10, history.size)
        assertEquals("Customer 13", history.first().query)
        assertEquals("Customer 4", history.last().query)
        assertFalse(history.any { it.query == "Customer 1" || it.query == "Customer 2" || it.query == "Customer 3" })
    }

    @Test
    fun `delete entry and clear history affect only targeted entry or module`() = runBlocking {
        searchRepo.recordSearch(SearchModule.INVENTORY, "Flour")
        delay(5L)
        searchRepo.recordSearch(SearchModule.INVENTORY, "Salt")
        searchRepo.recordSearch(SearchModule.INVOICE, "INV-007")

        val invList = searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY)
        assertEquals(2, invList.size)
        val flourEntry = invList.first { it.query == "Flour" }
        searchRepo.deleteSearchEntry(flourEntry.id)

        assertEquals(listOf("Salt"), searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY).map { it.query })
        assertEquals(listOf("INV-007"), searchRepo.getRecentSearchesOnce(SearchModule.INVOICE).map { it.query })

        searchRepo.clearHistory(SearchModule.INVENTORY)
        assertTrue(searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY).isEmpty())
        assertEquals(listOf("INV-007"), searchRepo.getRecentSearchesOnce(SearchModule.INVOICE).map { it.query })
    }

    @Test
    fun `search history is isolated across multiple businesses`() = runBlocking {
        businessRepo.saveBusinessProfile(BusinessProfile(businessName = "First Store"), completeOnboarding = true)
        searchRepo.recordSearch(SearchModule.INVENTORY, "Biz1 Item")

        val biz2Id = businessRepo.createBusinessProfile(
            BusinessProfile(businessName = "Second Store"),
            switchToNewBusiness = true
        )
        assertTrue(biz2Id > 1L)
        assertTrue(searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY).isEmpty())

        searchRepo.recordSearch(SearchModule.INVENTORY, "Biz2 Item")
        assertEquals(listOf("Biz2 Item"), searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY).map { it.query })

        businessRepo.switchActiveBusiness(1L).getOrThrow()
        assertEquals(listOf("Biz1 Item"), searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY).map { it.query })
    }

    @Test
    fun `migration 3 to 4 creates search_history table and indexes cleanly`() {
        val migDbName = "hisabpulse_mig_3_4_unit.db"
        context.deleteDatabase(migDbName)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS dummy_v3_data (id INTEGER PRIMARY KEY NOT NULL, note TEXT NOT NULL)")
                        db.execSQL("INSERT INTO dummy_v3_data (id, note) VALUES (1, 'preserved')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val rawDb = helper.writableDatabase
        HisabPulseDatabase.MIGRATION_3_4.migrate(rawDb)

        val tableCursor = rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='search_history'")
        assertTrue("search_history table must exist after MIGRATION_3_4", tableCursor.moveToFirst())
        tableCursor.close()

        val idxCursor = rawDb.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='search_history'")
        val indexes = mutableSetOf<String>()
        while (idxCursor.moveToNext()) {
            indexes += idxCursor.getString(0)
        }
        idxCursor.close()

        assertTrue("index_search_history_businessId" in indexes)
        assertTrue("index_search_history_businessId_module_searchedAtMillis" in indexes)
        assertTrue("index_search_history_businessId_module_query" in indexes)

        val existingCursor = rawDb.query("SELECT note FROM dummy_v3_data WHERE id = 1")
        assertTrue(existingCursor.moveToFirst())
        assertEquals("preserved", existingCursor.getString(0))
        existingCursor.close()

        helper.close()
    }

    @Test
    fun `v4 encrypted backup roundtrip preserves search history across modules`() = runBlocking {
        searchRepo.recordSearch(SearchModule.INVENTORY, "Basmati Rice")
        searchRepo.recordSearch(SearchModule.INVOICE, "Sharma Traders")

        val encryptedBytes = backupRepo.createBackupBytes(encrypt = true, passphrase = "StrongPass#2026")
        val snapshot = backupRepo.decodeAndValidateBackupBytes(encryptedBytes, "StrongPass#2026")
        assertEquals(5, snapshot.schemaVersion)
        assertEquals(2, snapshot.searchHistories.size)

        searchRepo.clearHistory(SearchModule.INVENTORY)
        searchRepo.clearHistory(SearchModule.INVOICE)
        assertTrue(searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY).isEmpty())

        val restoreResult = backupRepo.restoreFromSnapshot(snapshot)
        assertTrue(restoreResult.isSuccess)
        assertEquals(listOf("Basmati Rice"), searchRepo.getRecentSearchesOnce(SearchModule.INVENTORY).map { it.query })
        assertEquals(listOf("Sharma Traders"), searchRepo.getRecentSearchesOnce(SearchModule.INVOICE).map { it.query })
    }
}
