package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.remote.FirestoreProvider
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class FirestoreOperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: FirestoreOperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val shopName: String = "",
    val address: String = "",
    val phone: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class UserProfileRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(FirestoreProvider.getInstance(context))

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing cloud profile.")
    }

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, FirestoreOperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun getUserProfile(): UserProfile? {
        val uid = auth.currentUser?.uid ?: return null
        val docRef = db.collection("users").document(uid)
        return try {
            val cached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (cached != null && cached.exists()) {
                return cached.toObject(UserProfile::class.java)?.let { parsed ->
                    parsed.copy(
                        userId = parsed.userId.ifBlank { uid },
                        email = parsed.email.ifBlank { auth.currentUser?.email ?: "" },
                        phone = parsed.phone.ifBlank { auth.currentUser?.phoneNumber ?: "" }
                    )
                }
            }
            val doc = withTimeoutOrNull(2500L) { docRef.get(Source.DEFAULT).await() }
                ?: runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            if (doc != null && doc.exists()) {
                doc.toObject(UserProfile::class.java)?.let { parsed ->
                    parsed.copy(
                        userId = parsed.userId.ifBlank { uid },
                        email = parsed.email.ifBlank { auth.currentUser?.email ?: "" },
                        phone = parsed.phone.ifBlank { auth.currentUser?.phoneNumber ?: "" }
                    )
                }
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOperationType.GET, docRef.path)
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val cached = runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
            val existing = if (cached != null && cached.exists()) {
                cached
            } else {
                withTimeoutOrNull(2500L) {
                    runCatching { docRef.get(Source.DEFAULT).await() }.getOrNull()
                }
            }
            if (existing != null && existing.exists()) {
                val updatePayload = mapOf(
                    "email" to profile.email.trim(),
                    "shopName" to profile.shopName.trim(),
                    "address" to profile.address.trim(),
                    "phone" to profile.phone.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = docRef.update(updatePayload)
                    .addOnFailureListener { handleFirestoreError(it, FirestoreOperationType.UPDATE, docRef.path) }
                withTimeoutOrNull(2500L) { task.await() }
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "email" to profile.email.trim(),
                    "shopName" to profile.shopName.trim(),
                    "address" to profile.address.trim(),
                    "phone" to profile.phone.trim(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = docRef.set(createPayload)
                    .addOnFailureListener { handleFirestoreError(it, FirestoreOperationType.CREATE, docRef.path) }
                withTimeoutOrNull(2500L) { task.await() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val op = if (e is FirebaseFirestoreException) FirestoreOperationType.WRITE else FirestoreOperationType.UPDATE
            handleFirestoreError(e, op, docRef.path)
            throw e
        }
    }
}
