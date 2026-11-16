package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.HisabPulseDatabase
import com.example.data.local.entities.AppSettings
import com.example.data.local.entities.BusinessCategory
import com.example.data.local.entities.BusinessProfile
import com.example.data.local.entities.Invoice
import com.example.data.local.entities.InvoiceDocumentType
import com.example.data.local.entities.InvoiceItem
import com.example.data.local.entities.InvoiceStatus
import com.example.data.local.entities.PaymentMode
import com.example.data.local.entities.Product
import com.example.data.remote.FirestoreProvider
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

@IgnoreExtraProperties
data class SyncedBusiness(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 1L,
    val businessName: String = "",
    val ownerName: String = "",
    val tagline: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pinCode: String = "",
    val category: String = BusinessCategory.RETAIL.name,
    val isGstRegistered: Boolean = true,
    val gstin: String = "",
    val financialYear: String = "FY 2026-27",
    val currencySymbol: String = "₹",
    val invoicePrefix: String = "INV-",
    val nextInvoiceNumber: Long = 1001L,
    val upiId: String = "",
    val signatureLabel: String = "Authorized Signatory",
    val defaultTerms: String = "Thank you for your business!",
    val localUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedProduct(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val businessId: Long = 1L,
    val name: String = "",
    val sku: String = "",
    val barcode: String = "",
    val productCode: String = "",
    val hsnSacCode: String = "",
    val categoryName: String = "General",
    val brand: String = "",
    val description: String = "",
    val purchasePricePaise: Long = 0L,
    val sellingPricePaise: Long = 0L,
    val wholesalePricePaise: Long = 0L,
    val wholesaleMinQtyScaled: Long = 1000L,
    val mrpPaise: Long = 0L,
    val taxRatePercentScaled: Long = 0L,
    val isTaxInclusive: Boolean = false,
    val unitName: String = "PCS",
    val openingStockScaled: Long = 0L,
    val currentStockScaled: Long = 0L,
    val minStockScaled: Long = 500L,
    val batchNumber: String = "",
    val expiryDateMillis: Long? = null,
    val garmentSize: String = "",
    val garmentColor: String = "",
    val garmentStyle: String = "",
    val serialNumber: String = "",
    val warrantyMonths: Int = 0,
    val isServiceItem: Boolean = false,
    val isRawMaterial: Boolean = false,
    val defaultDiscountPercentScaled: Long = 0L,
    val trackExpiry: Boolean = false,
    val isActive: Boolean = true,
    val localCreatedAt: Long = 0L,
    val localUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedInvoice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val businessId: Long = 1L,
    val invoiceNumber: String = "",
    val documentType: String = InvoiceDocumentType.TAX_INVOICE.name,
    val status: String = InvoiceStatus.FINALIZED.name,
    val invoiceDateMillis: Long = 0L,
    val dueDateMillis: Long? = null,
    val customerId: Long? = null,
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerGstin: String = "",
    val isInterState: Boolean = false,
    val subtotalPaise: Long = 0L,
    val itemDiscountPaise: Long = 0L,
    val invoiceDiscountPercentScaled: Long = 0L,
    val invoiceDiscountFlatPaise: Long = 0L,
    val taxableAmountPaise: Long = 0L,
    val cgstPaise: Long = 0L,
    val sgstPaise: Long = 0L,
    val igstPaise: Long = 0L,
    val totalTaxPaise: Long = 0L,
    val additionalChargesPaise: Long = 0L,
    val shippingChargesPaise: Long = 0L,
    val roundOffPaise: Long = 0L,
    val grandTotalPaise: Long = 0L,
    val paidAmountPaise: Long = 0L,
    val balanceDuePaise: Long = 0L,
    val paymentMode: String = PaymentMode.CASH.name,
    val paymentReference: String = "",
    val loyaltyPointsEarned: Long = 0L,
    val loyaltyPointsRedeemed: Long = 0L,
    val loyaltyDiscountPaise: Long = 0L,
    val tableNumber: String = "",
    val kotStatus: String = "",
    val linkedOriginalInvoiceId: Long? = null,
    val notes: String = "",
    val termsAndConditions: String = "",
    val localCreatedAt: Long = 0L,
    val localUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SyncedInvoiceItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val invoiceLocalId: Long = 0L,
    val productId: Long? = null,
    val batchId: Long? = null,
    val productName: String = "",
    val productCode: String = "",
    val hsnSacCode: String = "",
    val unitName: String = "PCS",
    val quantityScaled: Long = 100L,
    val ratePaise: Long = 0L,
    val purchaseCostPaise: Long = 0L,
    val discountPercentScaled: Long = 0L,
    val discountAmountPaise: Long = 0L,
    val taxRatePercentScaled: Long = 0L,
    val isTaxInclusive: Boolean = false,
    val taxableAmountPaise: Long = 0L,
    val cgstPaise: Long = 0L,
    val sgstPaise: Long = 0L,
    val igstPaise: Long = 0L,
    val lineTotalPaise: Long = 0L,
    val batchNumber: String = "",
    val expiryDateMillis: Long? = null,
    val serialNumber: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CloudSyncStatus(
    val isStarted: Boolean = false,
    val isSyncing: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = true,
    val lastSyncedAtMillis: Long = 0L,
    val syncedBusinessesCount: Int = 0,
    val syncedProductsCount: Int = 0,
    val syncedInvoicesCount: Int = 0,
    val lastError: String? = null
)

class CloudSyncRepository private constructor(
    firestoreProvider: () -> FirebaseFirestore,
    authProvider: () -> FirebaseAuth,
    private val db: HisabPulseDatabase? = null
) {
    constructor(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        db: HisabPulseDatabase? = null
    ) : this(
        firestoreProvider = { firestore },
        authProvider = { auth },
        db = db
    )

    constructor(context: Context, db: HisabPulseDatabase) : this(
        firestoreProvider = { FirestoreProvider.getInstance(context) },
        authProvider = { Firebase.auth },
        db = db
    )

    private val firestore: FirebaseFirestore by lazy(firestoreProvider)
    private val auth: FirebaseAuth by lazy(authProvider)

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mergeMutex = Mutex()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus())
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    @Volatile
    private var activeUid: String? = null
    private var businessListener: ListenerRegistration? = null
    private var productListener: ListenerRegistration? = null
    private var invoiceListener: ListenerRegistration? = null

    private val knownBusinessDocIds = ConcurrentHashMap.newKeySet<String>()
    private val knownProductDocIds = ConcurrentHashMap.newKeySet<String>()
    private val knownInvoiceDocIds = ConcurrentHashMap.newKeySet<String>()

    private var businessPendingWrites = false
    private var productPendingWrites = false
    private var invoicePendingWrites = false
    private var businessFromCache = true
    private var productFromCache = true
    private var invoiceFromCache = true

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing cloud sync.")
    }

    fun startSyncForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        startSyncForUser(uid)
    }

    @Synchronized
    fun startSyncForUser(uid: String) {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return
        if (activeUid == uid && businessListener != null) return

        stopSync()
        activeUid = uid
        _syncStatus.update { it.copy(isStarted = true, isSyncing = true, lastError = null) }

        val userDoc = firestore.collection("users").document(uid)

        businessListener = userDoc.collection("businesses")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, FirestoreOperationType.LIST, "users/$uid/businesses")
                        _syncStatus.update { it.copy(isSyncing = false, lastError = error.localizedMessage) }
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                businessPendingWrites = snapshot.metadata.hasPendingWrites()
                businessFromCache = snapshot.metadata.isFromCache
                snapshot.documents.forEach { knownBusinessDocIds.add(it.id) }
                refreshCombinedSyncStatus(businessesCount = snapshot.size())

                val items = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(SyncedBusiness::class.java) }.getOrNull()
                }
                syncScope.launch {
                    mergeBusinessesIntoRoom(items, snapshot.metadata.hasPendingWrites())
                }
            }

        productListener = userDoc.collection("products")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, FirestoreOperationType.LIST, "users/$uid/products")
                        _syncStatus.update { it.copy(isSyncing = false, lastError = error.localizedMessage) }
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                productPendingWrites = snapshot.metadata.hasPendingWrites()
                productFromCache = snapshot.metadata.isFromCache
                snapshot.documents.forEach { knownProductDocIds.add(it.id) }
                refreshCombinedSyncStatus(productsCount = snapshot.size())

                val items = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(SyncedProduct::class.java) }.getOrNull()
                }
                syncScope.launch {
                    mergeProductsIntoRoom(items, snapshot.metadata.hasPendingWrites())
                }
            }

        invoiceListener = userDoc.collection("invoices")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, FirestoreOperationType.LIST, "users/$uid/invoices")
                        _syncStatus.update { it.copy(isSyncing = false, lastError = error.localizedMessage) }
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                invoicePendingWrites = snapshot.metadata.hasPendingWrites()
                invoiceFromCache = snapshot.metadata.isFromCache
                snapshot.documents.forEach { knownInvoiceDocIds.add(it.id) }
                refreshCombinedSyncStatus(invoicesCount = snapshot.size())

                val items = snapshot.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(SyncedInvoice::class.java) }.getOrNull()
                }
                syncScope.launch {
                    mergeInvoicesIntoRoom(uid, items, snapshot.metadata.hasPendingWrites())
                }
            }

        syncScope.launch {
            bootstrapUnsyncedLocalData()
        }
    }

    @Synchronized
    fun stopSync() {
        businessListener?.remove()
        productListener?.remove()
        invoiceListener?.remove()
        businessListener = null
        productListener = null
        invoiceListener = null
        activeUid = null
        knownBusinessDocIds.clear()
        knownProductDocIds.clear()
        knownInvoiceDocIds.clear()
        businessPendingWrites = false
        productPendingWrites = false
        invoicePendingWrites = false
        businessFromCache = true
        productFromCache = true
        invoiceFromCache = true
        _syncStatus.value = CloudSyncStatus()
    }

    private fun refreshCombinedSyncStatus(
        businessesCount: Int? = null,
        productsCount: Int? = null,
        invoicesCount: Int? = null
    ) {
        val anyPending = businessPendingWrites || productPendingWrites || invoicePendingWrites
        val allCached = businessFromCache && productFromCache && invoiceFromCache
        _syncStatus.update { cur ->
            cur.copy(
                isStarted = activeUid != null,
                isSyncing = false,
                hasPendingWrites = anyPending,
                isFromCache = allCached,
                lastSyncedAtMillis = if (!anyPending && !allCached) System.currentTimeMillis() else cur.lastSyncedAtMillis,
                syncedBusinessesCount = businessesCount ?: cur.syncedBusinessesCount,
                syncedProductsCount = productsCount ?: cur.syncedProductsCount,
                syncedInvoicesCount = invoicesCount ?: cur.syncedInvoicesCount,
                lastError = null
            )
        }
    }

    fun observeBusinesses(): Flow<List<SyncedBusiness>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/businesses"
        emitAll(
            firestore.collection("users").document(uid).collection("businesses")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            SyncedBusiness::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, FirestoreOperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProducts(): Flow<List<SyncedProduct>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/products"
        emitAll(
            firestore.collection("users").document(uid).collection("products")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            SyncedProduct::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, FirestoreOperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeInvoices(): Flow<List<SyncedInvoice>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/invoices"
        emitAll(
            firestore.collection("users").document(uid).collection("invoices")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            SyncedInvoice::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, FirestoreOperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun enqueueBusinessSync(profile: BusinessProfile) {}
    fun enqueueProductSync(product: Product) {}
    fun enqueueInvoiceSync(invoice: Invoice, items: List<InvoiceItem>) {}

    suspend fun pushBusinessNow(profile: BusinessProfile): Result<String> = Result.success("local_only")
    suspend fun pushProductNow(product: Product): Result<String> = Result.success("local_only")
    suspend fun pushInvoiceNow(invoice: Invoice, items: List<InvoiceItem>): Result<String> = Result.success("local_only")

    suspend fun getInvoiceItemsFromCloud(invoiceDocId: String): Result<List<SyncedInvoiceItem>> = Result.success(emptyList())

    suspend fun syncAllLocalToCloudNow(): Result<Unit> = Result.success(Unit)

    private suspend fun bootstrapUnsyncedLocalData() {}

    private suspend fun docExistsLocallyOrQuickly(
        docRef: com.google.firebase.firestore.DocumentReference,
        knownSet: MutableSet<String>
    ): Boolean {
        if (docRef.id in knownSet) return true
        val cached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
        if (cached != null && cached.exists()) {
            knownSet.add(docRef.id)
            return true
        }
        val remote = withTimeoutOrNull(1800L) {
            runCatching { docRef.get(Source.DEFAULT).await() }.getOrNull()
        }
        if (remote != null && remote.exists()) {
            knownSet.add(docRef.id)
            return true
        }
        return false
    }

    private suspend fun pushBusinessInternal(
        uid: String,
        profile: BusinessProfile,
        awaitServerAck: Boolean
    ): String {
        val docId = businessDocId(profile.id.coerceAtLeast(1L))
        val docRef = firestore.collection("users").document(uid)
            .collection("businesses").document(docId)

        val mutableFields = mutableMapOf<String, Any?>(
            "businessName" to profile.businessName.trim().ifBlank { "My Shop" }.take(120),
            "ownerName" to profile.ownerName.trim().take(100),
            "tagline" to profile.tagline.trim().take(160),
            "phone" to profile.phone.trim().take(32),
            "email" to profile.email.trim().take(128),
            "website" to profile.website.trim().take(160),
            "address" to profile.address.trim().take(300),
            "city" to profile.city.trim().take(80),
            "state" to profile.state.trim().take(80),
            "pinCode" to profile.pinCode.trim().take(16),
            "category" to profile.category.name,
            "isGstRegistered" to profile.isGstRegistered,
            "gstin" to profile.gstin.trim().take(24),
            "financialYear" to profile.financialYear.trim().take(24),
            "currencySymbol" to profile.currencySymbol.trim().ifBlank { "₹" }.take(8),
            "invoicePrefix" to profile.invoicePrefix.trim().take(16),
            "nextInvoiceNumber" to profile.nextInvoiceNumber.coerceIn(1L, 1_000_000_000L),
            "upiId" to profile.upiId.trim().take(128),
            "signatureLabel" to profile.signatureLabel.trim().take(80),
            "defaultTerms" to profile.defaultTerms.trim().take(500),
            "localUpdatedAt" to profile.updatedAt.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val exists = docExistsLocallyOrQuickly(docRef, knownBusinessDocIds)
        try {
            val task = if (exists) {
                docRef.update(mutableFields)
            } else {
                val createMap = mutableFields + mapOf(
                    "userId" to uid,
                    "localId" to profile.id.coerceAtLeast(1L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                knownBusinessDocIds.add(docId)
                docRef.set(createMap)
            }
            task.addOnFailureListener { e ->
                handleFirestoreError(e, if (exists) FirestoreOperationType.UPDATE else FirestoreOperationType.CREATE, docRef.path)
                _syncStatus.update { it.copy(lastError = e.localizedMessage) }
            }
            if (awaitServerAck) {
                task.await()
            }
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (exists) FirestoreOperationType.UPDATE else FirestoreOperationType.CREATE, docRef.path)
            throw e
        }
    }

    private suspend fun pushProductInternal(
        uid: String,
        product: Product,
        awaitServerAck: Boolean
    ): String {
        val localId = product.id.coerceAtLeast(1L)
        val docId = productDocId(localId)
        val docRef = firestore.collection("users").document(uid)
            .collection("products").document(docId)

        val mutableFields = mutableMapOf<String, Any?>(
            "name" to product.name.trim().ifBlank { "Item" }.take(160),
            "sku" to product.sku.trim().take(64),
            "barcode" to product.barcode.trim().take(64),
            "productCode" to product.productCode.trim().take(64),
            "hsnSacCode" to product.hsnSacCode.trim().take(32),
            "categoryName" to product.categoryName.trim().ifBlank { "General" }.take(80),
            "brand" to product.brand.trim().take(80),
            "description" to product.description.trim().take(500),
            "purchasePricePaise" to product.purchasePricePaise.coerceIn(0L, 100_000_000_000L),
            "sellingPricePaise" to product.sellingPricePaise.coerceIn(0L, 100_000_000_000L),
            "wholesalePricePaise" to product.wholesalePricePaise.coerceIn(0L, 100_000_000_000L),
            "wholesaleMinQtyScaled" to product.wholesaleMinQtyScaled.coerceIn(0L, 100_000_000L),
            "mrpPaise" to product.mrpPaise.coerceIn(0L, 100_000_000_000L),
            "taxRatePercentScaled" to product.taxRatePercentScaled.coerceIn(0L, 10_000L),
            "isTaxInclusive" to product.isTaxInclusive,
            "unitName" to product.unitName.trim().ifBlank { "PCS" }.take(24),
            "openingStockScaled" to product.openingStockScaled.coerceIn(-100_000_000L, 1_000_000_000L),
            "currentStockScaled" to product.currentStockScaled.coerceIn(-100_000_000L, 1_000_000_000L),
            "minStockScaled" to product.minStockScaled.coerceIn(0L, 1_000_000_000L),
            "batchNumber" to product.batchNumber.trim().take(64),
            "expiryDateMillis" to product.expiryDateMillis?.coerceAtLeast(0L),
            "garmentSize" to product.garmentSize.trim().take(32),
            "garmentColor" to product.garmentColor.trim().take(32),
            "garmentStyle" to product.garmentStyle.trim().take(48),
            "serialNumber" to product.serialNumber.trim().take(80),
            "warrantyMonths" to product.warrantyMonths.coerceIn(0, 1200),
            "isServiceItem" to product.isServiceItem,
            "isRawMaterial" to product.isRawMaterial,
            "defaultDiscountPercentScaled" to product.defaultDiscountPercentScaled.coerceIn(0L, 10_000L),
            "trackExpiry" to product.trackExpiry,
            "isActive" to product.isActive,
            "localUpdatedAt" to product.updatedAt.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val exists = docExistsLocallyOrQuickly(docRef, knownProductDocIds)
        try {
            val task = if (exists) {
                docRef.update(mutableFields)
            } else {
                val createMap = mutableFields + mapOf(
                    "userId" to uid,
                    "localId" to localId,
                    "businessId" to product.businessId.coerceAtLeast(1L),
                    "localCreatedAt" to product.createdAt.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                knownProductDocIds.add(docId)
                docRef.set(createMap)
            }
            task.addOnFailureListener { e ->
                handleFirestoreError(e, if (exists) FirestoreOperationType.UPDATE else FirestoreOperationType.CREATE, docRef.path)
                _syncStatus.update { it.copy(lastError = e.localizedMessage) }
            }
            if (awaitServerAck) {
                task.await()
            }
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (exists) FirestoreOperationType.UPDATE else FirestoreOperationType.CREATE, docRef.path)
            throw e
        }
    }

    private suspend fun pushInvoiceInternal(
        uid: String,
        invoice: Invoice,
        items: List<InvoiceItem>,
        awaitServerAck: Boolean
    ): String {
        val localId = invoice.id.coerceAtLeast(1L)
        val docId = invoiceDocId(localId)
        val docRef = firestore.collection("users").document(uid)
            .collection("invoices").document(docId)

        val mutableFields = mutableMapOf<String, Any?>(
            "invoiceNumber" to invoice.invoiceNumber.trim().ifBlank { "INV-$localId" }.take(64),
            "documentType" to invoice.documentType.name,
            "status" to invoice.status.name,
            "invoiceDateMillis" to invoice.invoiceDateMillis.coerceAtLeast(0L),
            "dueDateMillis" to invoice.dueDateMillis?.coerceAtLeast(0L),
            "customerId" to invoice.customerId?.coerceAtLeast(0L),
            "customerName" to invoice.customerName.trim().ifBlank { "Walk-in Customer" }.take(120),
            "customerPhone" to invoice.customerPhone.trim().take(32),
            "customerAddress" to invoice.customerAddress.trim().take(300),
            "customerGstin" to invoice.customerGstin.trim().take(24),
            "isInterState" to invoice.isInterState,
            "subtotalPaise" to invoice.subtotalPaise.coerceIn(0L, 100_000_000_000L),
            "itemDiscountPaise" to invoice.itemDiscountPaise.coerceIn(0L, 100_000_000_000L),
            "invoiceDiscountPercentScaled" to invoice.invoiceDiscountPercentScaled.coerceIn(0L, 10_000L),
            "invoiceDiscountFlatPaise" to invoice.invoiceDiscountFlatPaise.coerceIn(0L, 100_000_000_000L),
            "taxableAmountPaise" to invoice.taxableAmountPaise.coerceIn(0L, 100_000_000_000L),
            "cgstPaise" to invoice.cgstPaise.coerceIn(0L, 100_000_000_000L),
            "sgstPaise" to invoice.sgstPaise.coerceIn(0L, 100_000_000_000L),
            "igstPaise" to invoice.igstPaise.coerceIn(0L, 100_000_000_000L),
            "totalTaxPaise" to invoice.totalTaxPaise.coerceIn(0L, 100_000_000_000L),
            "additionalChargesPaise" to invoice.additionalChargesPaise.coerceIn(0L, 100_000_000_000L),
            "shippingChargesPaise" to invoice.shippingChargesPaise.coerceIn(0L, 100_000_000_000L),
            "roundOffPaise" to invoice.roundOffPaise.coerceIn(-10_000L, 10_000L),
            "grandTotalPaise" to invoice.grandTotalPaise.coerceIn(0L, 100_000_000_000L),
            "paidAmountPaise" to invoice.paidAmountPaise.coerceIn(0L, 100_000_000_000L),
            "balanceDuePaise" to invoice.balanceDuePaise.coerceIn(0L, 100_000_000_000L),
            "paymentMode" to invoice.paymentMode.name,
            "paymentReference" to invoice.paymentReference.trim().take(120),
            "loyaltyPointsEarned" to invoice.loyaltyPointsEarned.coerceIn(0L, 100_000_000L),
            "loyaltyPointsRedeemed" to invoice.loyaltyPointsRedeemed.coerceIn(0L, 100_000_000L),
            "loyaltyDiscountPaise" to invoice.loyaltyDiscountPaise.coerceIn(0L, 100_000_000_000L),
            "tableNumber" to invoice.tableNumber.trim().take(32),
            "kotStatus" to invoice.kotStatus.trim().take(32),
            "linkedOriginalInvoiceId" to invoice.linkedOriginalInvoiceId?.coerceAtLeast(0L),
            "notes" to invoice.notes.trim().take(500),
            "termsAndConditions" to invoice.termsAndConditions.trim().take(500),
            "localUpdatedAt" to invoice.updatedAt.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val exists = docExistsLocallyOrQuickly(docRef, knownInvoiceDocIds)
        try {
            val batch = firestore.batch()
            if (exists) {
                batch.update(docRef, mutableFields)
            } else {
                val createMap = mutableFields + mapOf(
                    "userId" to uid,
                    "localId" to localId,
                    "businessId" to invoice.businessId.coerceAtLeast(1L),
                    "localCreatedAt" to invoice.createdAt.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                knownInvoiceDocIds.add(docId)
                batch.set(docRef, createMap)
            }

            val itemsCol = docRef.collection("items")
            val existingItemDocs = if (exists) {
                val cached = runCatching { itemsCol.get(Source.CACHE).await() }.getOrNull()
                if (cached != null && !cached.isEmpty) {
                    cached.documents
                } else {
                    withTimeoutOrNull(1800L) {
                        runCatching { itemsCol.get(Source.DEFAULT).await() }.getOrNull()?.documents
                    }.orEmpty()
                }
            } else {
                emptyList()
            }
            val existingItemIds = existingItemDocs.map { it.id }.toSet()
            val newItemIds = mutableSetOf<String>()

            items.forEachIndexed { index, item ->
                val itemDocId = "item_$index"
                newItemIds.add(itemDocId)
                val itemRef = itemsCol.document(itemDocId)
                val mutableItemFields = mapOf<String, Any?>(
                    "productId" to item.productId?.coerceAtLeast(0L),
                    "batchId" to item.batchId?.coerceAtLeast(0L),
                    "productName" to item.productName.trim().ifBlank { "Item" }.take(160),
                    "productCode" to item.productCode.trim().take(64),
                    "hsnSacCode" to item.hsnSacCode.trim().take(32),
                    "unitName" to item.unitName.trim().ifBlank { "PCS" }.take(24),
                    "quantityScaled" to item.quantityScaled.coerceIn(1L, 1_000_000_000L),
                    "ratePaise" to item.ratePaise.coerceIn(0L, 100_000_000_000L),
                    "purchaseCostPaise" to item.purchaseCostPaise.coerceIn(0L, 100_000_000_000L),
                    "discountPercentScaled" to item.discountPercentScaled.coerceIn(0L, 10_000L),
                    "discountAmountPaise" to item.discountAmountPaise.coerceIn(0L, 100_000_000_000L),
                    "taxRatePercentScaled" to item.taxRatePercentScaled.coerceIn(0L, 10_000L),
                    "isTaxInclusive" to item.isTaxInclusive,
                    "taxableAmountPaise" to item.taxableAmountPaise.coerceIn(0L, 100_000_000_000L),
                    "cgstPaise" to item.cgstPaise.coerceIn(0L, 100_000_000_000L),
                    "sgstPaise" to item.sgstPaise.coerceIn(0L, 100_000_000_000L),
                    "igstPaise" to item.igstPaise.coerceIn(0L, 100_000_000_000L),
                    "lineTotalPaise" to item.lineTotalPaise.coerceIn(0L, 100_000_000_000L),
                    "batchNumber" to item.batchNumber.trim().take(64),
                    "expiryDateMillis" to item.expiryDateMillis?.coerceAtLeast(0L),
                    "serialNumber" to item.serialNumber.trim().take(80),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (itemDocId in existingItemIds) {
                    batch.update(itemRef, mutableItemFields)
                } else {
                    val createItemFields = mutableItemFields + mapOf(
                        "userId" to uid,
                        "invoiceLocalId" to localId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(itemRef, createItemFields)
                }
            }
            existingItemDocs
                .filter { it.id !in newItemIds }
                .forEach { staleDoc -> batch.delete(staleDoc.reference) }

            val task = batch.commit()
            task.addOnFailureListener { e ->
                handleFirestoreError(e, if (exists) FirestoreOperationType.UPDATE else FirestoreOperationType.CREATE, docRef.path)
                _syncStatus.update { it.copy(lastError = e.localizedMessage) }
            }
            if (awaitServerAck) {
                task.await()
            }
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (exists) FirestoreOperationType.UPDATE else FirestoreOperationType.CREATE, docRef.path)
            throw e
        }
    }

    private suspend fun mergeBusinessesIntoRoom(
        remoteList: List<SyncedBusiness>,
        hasPendingWrites: Boolean
    ) {
        val localDb = db ?: return
        if (remoteList.isEmpty()) return
        mergeMutex.withLock {
            runCatching {
                val coreDao = localDb.coreDao()
                localDb.withTransaction {
                    for (remote in remoteList) {
                        val existing = coreDao.getBusinessProfileByIdOnce(remote.localId)
                        if (hasPendingWrites && existing != null && existing.updatedAt >= remote.localUpdatedAt) {
                            continue
                        }
                        if (existing == null || remote.localUpdatedAt > existing.updatedAt) {
                            val merged = BusinessProfile(
                                id = remote.localId,
                                businessName = remote.businessName.ifBlank { existing?.businessName ?: "My Shop" },
                                ownerName = remote.ownerName,
                                tagline = remote.tagline,
                                logoUri = existing?.logoUri ?: "",
                                phone = remote.phone,
                                email = remote.email,
                                website = remote.website,
                                address = remote.address,
                                city = remote.city,
                                state = remote.state,
                                pinCode = remote.pinCode,
                                category = runCatching { BusinessCategory.valueOf(remote.category) }
                                    .getOrDefault(BusinessCategory.RETAIL),
                                isGstRegistered = remote.isGstRegistered,
                                gstin = remote.gstin,
                                financialYear = remote.financialYear,
                                currencySymbol = remote.currencySymbol.ifBlank { "₹" },
                                invoicePrefix = remote.invoicePrefix.ifBlank { "INV-" },
                                nextInvoiceNumber = maxOf(remote.nextInvoiceNumber, existing?.nextInvoiceNumber ?: 1001L),
                                upiId = remote.upiId,
                                signatureLabel = remote.signatureLabel,
                                defaultTerms = remote.defaultTerms,
                                signatureUri = existing?.signatureUri ?: "",
                                sealUri = existing?.sealUri ?: "",
                                updatedAt = remote.localUpdatedAt
                            )
                            coreDao.upsertBusinessProfile(merged)
                            val curSettings = coreDao.getAppSettingsOnce()
                            if (curSettings == null || !curSettings.onboardingCompleted) {
                                coreDao.upsertAppSettings(
                                    (curSettings ?: AppSettings(id = 1L)).copy(
                                        activeBusinessId = merged.id,
                                        onboardingCompleted = true,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
                for (remote in remoteList) {
                    HisabPulseDatabase.ensureBusinessSeeded(localDb, remote.localId)
                }
            }
        }
    }

    private suspend fun mergeProductsIntoRoom(
        remoteList: List<SyncedProduct>,
        hasPendingWrites: Boolean
    ) {
        val localDb = db ?: return
        if (remoteList.isEmpty()) return
        mergeMutex.withLock {
            runCatching {
                val inventoryDao = localDb.inventoryDao()
                localDb.withTransaction {
                    for (remote in remoteList) {
                        val existing = inventoryDao.getProductByIdOnce(remote.localId)
                        if (hasPendingWrites && existing != null && existing.updatedAt >= remote.localUpdatedAt) {
                            continue
                        }
                        if (existing == null || remote.localUpdatedAt > existing.updatedAt) {
                            val merged = Product(
                                id = remote.localId,
                                businessId = remote.businessId,
                                name = remote.name,
                                imageUri = existing?.imageUri ?: "",
                                sku = remote.sku,
                                barcode = remote.barcode,
                                productCode = remote.productCode,
                                hsnSacCode = remote.hsnSacCode,
                                categoryId = existing?.categoryId,
                                categoryName = remote.categoryName,
                                brand = remote.brand,
                                description = remote.description,
                                purchasePricePaise = remote.purchasePricePaise,
                                sellingPricePaise = remote.sellingPricePaise,
                                wholesalePricePaise = remote.wholesalePricePaise,
                                wholesaleMinQtyScaled = remote.wholesaleMinQtyScaled,
                                mrpPaise = remote.mrpPaise,
                                taxRatePercentScaled = remote.taxRatePercentScaled,
                                isTaxInclusive = remote.isTaxInclusive,
                                unitId = existing?.unitId,
                                unitName = remote.unitName,
                                openingStockScaled = remote.openingStockScaled,
                                currentStockScaled = remote.currentStockScaled,
                                minStockScaled = remote.minStockScaled,
                                batchNumber = remote.batchNumber,
                                expiryDateMillis = remote.expiryDateMillis,
                                garmentSize = remote.garmentSize,
                                garmentColor = remote.garmentColor,
                                garmentStyle = remote.garmentStyle,
                                serialNumber = remote.serialNumber,
                                warrantyMonths = remote.warrantyMonths,
                                isServiceItem = remote.isServiceItem,
                                isRawMaterial = remote.isRawMaterial,
                                defaultDiscountPercentScaled = remote.defaultDiscountPercentScaled,
                                trackExpiry = remote.trackExpiry,
                                isActive = remote.isActive,
                                createdAt = remote.localCreatedAt.takeIf { it > 0L } ?: existing?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = remote.localUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                            )
                            inventoryDao.insertProduct(merged)
                        }
                    }
                }
            }
        }
    }

    private suspend fun mergeInvoicesIntoRoom(
        uid: String,
        remoteList: List<SyncedInvoice>,
        hasPendingWrites: Boolean
    ) {
        val localDb = db ?: return
        if (remoteList.isEmpty()) return

        val billingDao = localDb.billingDao()
        val partyDao = localDb.partyDao()
        val inventoryDao = localDb.inventoryDao()

        val candidates = remoteList.filter { remote ->
            val existing = billingDao.getInvoiceByIdOnce(remote.localId)
                ?: billingDao.findInvoiceByNumber(remote.invoiceNumber, remote.businessId)
            when {
                hasPendingWrites && existing != null && existing.updatedAt >= remote.localUpdatedAt -> false
                existing == null -> true
                remote.localUpdatedAt > existing.updatedAt -> true
                else -> false
            }
        }
        if (candidates.isEmpty()) return

        val itemsByDocId = mutableMapOf<String, List<SyncedInvoiceItem>>()
        for (remote in candidates) {
            val docId = remote.id.ifBlank { invoiceDocId(remote.localId) }
            val itemsCol = firestore.collection("users").document(uid)
                .collection("invoices").document(docId).collection("items")
            val itemSnap = withTimeoutOrNull(2500L) {
                runCatching { itemsCol.get(Source.DEFAULT).await() }.getOrNull()
            } ?: runCatching { itemsCol.get(Source.CACHE).await() }.getOrNull()
            if (itemSnap != null) {
                itemsByDocId[docId] = itemSnap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(SyncedInvoiceItem::class.java) }.getOrNull()
                }
            }
        }

        mergeMutex.withLock {
            runCatching {
                localDb.withTransaction {
                    for (remote in candidates) {
                        val existing = billingDao.getInvoiceByIdOnce(remote.localId)
                            ?: billingDao.findInvoiceByNumber(remote.invoiceNumber, remote.businessId)
                        if (existing != null && remote.localUpdatedAt <= existing.updatedAt) continue

                        val safeCustomerId = remote.customerId?.let { cid ->
                            if (partyDao.getCustomerByIdOnce(cid) != null) cid else null
                        }
                        val targetId = existing?.id ?: remote.localId
                        val mergedInvoice = Invoice(
                            id = targetId,
                            businessId = remote.businessId,
                            invoiceNumber = remote.invoiceNumber,
                            documentType = runCatching { InvoiceDocumentType.valueOf(remote.documentType) }
                                .getOrDefault(InvoiceDocumentType.TAX_INVOICE),
                            status = runCatching { InvoiceStatus.valueOf(remote.status) }
                                .getOrDefault(InvoiceStatus.FINALIZED),
                            invoiceDateMillis = remote.invoiceDateMillis,
                            dueDateMillis = remote.dueDateMillis,
                            customerId = safeCustomerId,
                            customerName = remote.customerName,
                            customerPhone = remote.customerPhone,
                            customerAddress = remote.customerAddress,
                            customerGstin = remote.customerGstin,
                            isInterState = remote.isInterState,
                            subtotalPaise = remote.subtotalPaise,
                            itemDiscountPaise = remote.itemDiscountPaise,
                            invoiceDiscountPercentScaled = remote.invoiceDiscountPercentScaled,
                            invoiceDiscountFlatPaise = remote.invoiceDiscountFlatPaise,
                            taxableAmountPaise = remote.taxableAmountPaise,
                            cgstPaise = remote.cgstPaise,
                            sgstPaise = remote.sgstPaise,
                            igstPaise = remote.igstPaise,
                            totalTaxPaise = remote.totalTaxPaise,
                            additionalChargesPaise = remote.additionalChargesPaise,
                            shippingChargesPaise = remote.shippingChargesPaise,
                            roundOffPaise = remote.roundOffPaise,
                            grandTotalPaise = remote.grandTotalPaise,
                            paidAmountPaise = remote.paidAmountPaise,
                            balanceDuePaise = remote.balanceDuePaise,
                            paymentMode = runCatching { PaymentMode.valueOf(remote.paymentMode) }
                                .getOrDefault(PaymentMode.CASH),
                            paymentReference = remote.paymentReference,
                            loyaltyPointsEarned = remote.loyaltyPointsEarned,
                            loyaltyPointsRedeemed = remote.loyaltyPointsRedeemed,
                            loyaltyDiscountPaise = remote.loyaltyDiscountPaise,
                            tableNumber = remote.tableNumber,
                            kotStatus = remote.kotStatus,
                            linkedOriginalInvoiceId = remote.linkedOriginalInvoiceId,
                            notes = remote.notes,
                            termsAndConditions = remote.termsAndConditions,
                            createdAt = remote.localCreatedAt.takeIf { it > 0L } ?: existing?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = remote.localUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                        )
                        val savedInvId = billingDao.insertInvoice(mergedInvoice)
                        val docId = remote.id.ifBlank { invoiceDocId(remote.localId) }
                        val remoteItems = itemsByDocId[docId].orEmpty()
                        if (remoteItems.isNotEmpty()) {
                            billingDao.deleteInvoiceItems(savedInvId)
                            val mappedItems = remoteItems.map { item ->
                                val safeProductId = item.productId?.let { pid ->
                                    if (inventoryDao.getProductByIdOnce(pid) != null) pid else null
                                }
                                InvoiceItem(
                                    invoiceId = savedInvId,
                                    productId = safeProductId,
                                    batchId = null,
                                    productName = item.productName,
                                    productCode = item.productCode,
                                    hsnSacCode = item.hsnSacCode,
                                    unitName = item.unitName,
                                    quantityScaled = item.quantityScaled,
                                    ratePaise = item.ratePaise,
                                    purchaseCostPaise = item.purchaseCostPaise,
                                    discountPercentScaled = item.discountPercentScaled,
                                    discountAmountPaise = item.discountAmountPaise,
                                    taxRatePercentScaled = item.taxRatePercentScaled,
                                    isTaxInclusive = item.isTaxInclusive,
                                    taxableAmountPaise = item.taxableAmountPaise,
                                    cgstPaise = item.cgstPaise,
                                    sgstPaise = item.sgstPaise,
                                    igstPaise = item.igstPaise,
                                    lineTotalPaise = item.lineTotalPaise,
                                    batchNumber = item.batchNumber,
                                    expiryDateMillis = item.expiryDateMillis,
                                    serialNumber = item.serialNumber
                                )
                            }
                            billingDao.insertInvoiceItems(mappedItems)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun businessDocId(localId: Long): String = "biz_${localId.coerceAtLeast(1L)}"
        fun productDocId(localId: Long): String = "prod_${localId.coerceAtLeast(1L)}"
        fun invoiceDocId(localId: Long): String = "inv_${localId.coerceAtLeast(1L)}"
    }
}
