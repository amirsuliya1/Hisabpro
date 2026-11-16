package com.example.ui.screens.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.HisabPulseApp
import com.example.R
import com.example.data.local.entities.BusinessProfile
import com.example.data.repository.UserProfile
import com.example.data.repository.UserProfileRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun GoogleGLogo(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.width * 0.17f
        val pad = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(pad, pad)

        // Red (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 215f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Yellow (bottom-left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Green (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 35f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Blue (right & middle bar)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -12f,
            sweepAngle = 52f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        val midY = this.size.height / 2f
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(this.size.width * 0.50f, midY),
            end = Offset(this.size.width - pad * 0.35f, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Butt
        )
    }
}

fun signOutFromFirebaseAndCredentials(
    context: Context,
    scope: CoroutineScope,
    onComplete: () -> Unit = {}
) {
    val credentialManager = CredentialManager.create(context)
    val app = context.applicationContext as? HisabPulseApp
    scope.launch {
        withContext(NonCancellable) {
            try {
                app?.container?.cloudSyncRepository?.stopSync()
                app?.container?.preferences?.clearSessionCacheSync()
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onComplete()
            }
        }
    }
}

private fun friendlyGoogleAuthError(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        msg.contains("16") || msg.contains("CANNOT_BIND_TO_SERVICE") ->
            "Google Play Services connection cancelled. Please try again."
        msg.contains("NETWORK") || msg.contains("connection") ->
            "Network error connecting to Google. Please check your internet connection."
        else ->
            e.localizedMessage ?: "Google Sign-In was cancelled or failed. Please try again."
    }
}

@Composable
fun FirebaseAuthScreen(
    incomingEmailLink: String? = null,
    onConsumeEmailLink: () -> Unit = {},
    onAuthComplete: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as HisabPulseApp
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val userProfileRepo = remember { UserProfileRepository(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    // Cloud Profile setup state (Only Email, Shop Name, Address, Phone Number)
    var shopName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var profileEmail by remember { mutableStateOf("") }
    var shopPhone by remember { mutableStateOf("") }
    var checkingProfile by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Legal Disclosures Dialog state
    var activeLegalDialog by remember { mutableStateOf<LegalDialogType?>(null) }

    // Track Firebase Auth changes live
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    // Silent Google auto-sign-in attempt on startup when unauthenticated
    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser != null) return@LaunchedEffect
        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
            ?: return@LaunchedEffect
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCred = GoogleAuthProvider.getCredential(idToken, null)
                Firebase.auth.signInWithCredential(authCred).await()
            }
        } catch (_: Exception) {
            // Silent check fails cleanly when no previously authorized credential exists
        }
    }

    // Fetch existing Firestore / local user profile once authenticated with Google
    LaunchedEffect(currentUser?.uid) {
        val user = currentUser ?: return@LaunchedEffect
        profileEmail = user.email.orEmpty().ifBlank { profileEmail }
        shopPhone = user.phoneNumber.orEmpty().ifBlank { shopPhone }
        if (shopName.isBlank()) {
            shopName = user.displayName?.let { "$it's Store" } ?: ""
        }

        // Fast-path: if local Room business profile & onboarding exist, launch immediately without loading spinner
        val localBiz = runCatching { app.container.businessRepository.getBusinessProfileOnce() }.getOrNull()
        val localOnboarded = runCatching {
            app.container.preferences.isOnboardingCompletedSync() ||
                app.container.preferences.onboardingCompleted.first()
        }.getOrDefault(false)
        if (localBiz != null && localBiz.businessName.isNotBlank() &&
            (localOnboarded || app.container.preferences.hasCompletedSetupForUidSync(user.uid))
        ) {
            app.container.preferences.markSetupCompleteForUidSync(user.uid)
            app.container.cloudSyncRepository.startSyncForUser(user.uid)
            onAuthComplete()
            return@LaunchedEffect
        }

        checkingProfile = true
        val existingCloud = userProfileRepo.getUserProfile()
        if (existingCloud != null) {
            shopName = existingCloud.shopName
            address = existingCloud.address
            if (existingCloud.email.isNotBlank()) profileEmail = existingCloud.email
            if (existingCloud.phone.isNotBlank()) shopPhone = existingCloud.phone

            if (shopName.isNotBlank() && address.isNotBlank() && profileEmail.isNotBlank() && shopPhone.isNotBlank()) {
                runCatching {
                    val active = app.container.businessRepository.getBusinessProfileOnce()
                    val merged = (active ?: BusinessProfile(businessName = shopName)).copy(
                        businessName = shopName,
                        address = address,
                        email = profileEmail,
                        phone = shopPhone
                    )
                    app.container.businessRepository.saveBusinessProfile(merged, completeOnboarding = true)
                }
                app.container.preferences.markSetupCompleteForUidSync(user.uid)
                app.container.cloudSyncRepository.startSyncForUser(user.uid)
                checkingProfile = false
                onAuthComplete()
                return@LaunchedEffect
            }
        } else if (localBiz != null && localBiz.businessName.isNotBlank()) {
            // Returning offline user without prior fast-cache entry: preserve local business data instead of resetting
            app.container.businessRepository.saveBusinessProfile(localBiz, completeOnboarding = true)
            app.container.preferences.markSetupCompleteForUidSync(user.uid)
            app.container.cloudSyncRepository.startSyncForUser(user.uid)
            checkingProfile = false
            onAuthComplete()
            return@LaunchedEffect
        }
        checkingProfile = false
    }

    fun launchGoogleSignIn() {
        if (isGoogleSigningIn || isLoading) return
        val activity = context as? Activity
        if (activity == null) {
            errorMessage = "Activity unavailable for Google Sign-In."
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            errorMessage = "Google Sign-In configuration missing in app."
            return
        }
        isGoogleSigningIn = true
        isLoading = true
        errorMessage = null
        statusMessage = null
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Firebase.auth.signInWithCredential(authCredential).await()
                } else {
                    errorMessage = "Unexpected Google credential type."
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                errorMessage = friendlyGoogleAuthError(e)
            } finally {
                isLoading = false
                delay(1200L) // Anti-spam debounce
                isGoogleSigningIn = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // High-Quality Visual Background Image
        Image(
            painter = painterResource(id = R.drawable.img_login_bg),
            contentDescription = "Login Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Multi-Layer Dark Gradient Scrim for crisp readability & glassmorphism
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x990A0E17),
                            Color(0xCC0B1120),
                            Color(0xEB0B1120),
                            Color(0xFA080C14)
                        )
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Brand Header with Glowing Glassmorphic Logo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            shadowElevation = 8.dp,
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Color(0xFFF97316)),
                            modifier = Modifier.size(88.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_app_logo),
                                contentDescription = "HisabPulse Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "HisabPulse",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Smart GST Billing • Khata • Cloud Account",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        // Security Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x33000000),
                            border = BorderStroke(1.dp, Color(0x44FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(15.dp))
                                Text(
                                    text = "Official Google Account Sign-In • 100% Offline Ledger",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (checkingProfile) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Loading your shop profile...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else if (currentUser == null) {
                        // ── PURE GOOGLE SIGN-IN CARD ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Sign In to Your Business",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "One-tap secure authentication with your Google account. Zero passwords, zero SMS delays.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (errorMessage != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = errorMessage!!,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                // Primary Interactive Google Sign-In Button
                                Surface(
                                    onClick = { if (!isLoading && !isGoogleSigningIn) launchGoogleSignIn() },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
                                    shadowElevation = 3.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("google_signin_btn")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isGoogleSigningIn || isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Connecting Google Account...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F1F1F))
                                        } else {
                                            GoogleGLogo(size = 24.dp)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Continue with Google",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1F1F1F)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Feature Highlights
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FeatureHighlightRow(
                                        icon = Icons.Default.CheckCircle,
                                        title = "1-Tap Instant Access",
                                        desc = "Verified Google ID. No SMS codes or passwords required."
                                    )
                                    FeatureHighlightRow(
                                        icon = Icons.Default.Storage,
                                        title = "Offline-First Ledger & Billing",
                                        desc = "Create bills, manage inventory, and view reports even without internet."
                                    )
                                    FeatureHighlightRow(
                                        icon = Icons.Default.CloudDone,
                                        title = "Automatic Cloud Sync",
                                        desc = "Business settings, inventory & invoices cache locally and sync with Firebase."
                                    )
                                }
                            }
                        }

                        // ── PRIVACY POLICY, TERMS & LEGAL COMPLIANCE CARD ──
                        Surface(
                            color = Color(0x33000000),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Legal Disclosures & Privacy Guarantees",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Text(
                                    text = "By signing in, your business profile, inventory catalog, and invoices are persisted offline on your device and securely synced to your private Firebase account.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { activeLegalDialog = LegalDialogType.PRIVACY_POLICY },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Privacy Policy", style = MaterialTheme.typography.labelSmall)
                                    }

                                    OutlinedButton(
                                        onClick = { activeLegalDialog = LegalDialogType.TERMS_OF_SERVICE },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Terms & Legal", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    } else {
                        // ── AUTHENTICATED: SHOP DETAILS SETUP (4 FIELDS ONLY) ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Shop & Business Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Signed in as: ${currentUser?.email ?: currentUser?.uid}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Please enter the 4 official business details below. These are printed on your invoices and backed up to Firebase.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (errorMessage != null) {
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                // Field 1: Shop Name
                                OutlinedTextField(
                                    value = shopName,
                                    onValueChange = { shopName = it },
                                    label = { Text("Shop Name *") },
                                    placeholder = { Text("e.g. HisabPulse General Store") },
                                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("cloud_shop_name_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Field 2: Shop Address
                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { address = it },
                                    label = { Text("Shop Address *") },
                                    placeholder = { Text("e.g. Shop #12, Market Complex, Station Road") },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth().testTag("cloud_address_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Field 3: Shopkeeper Contact Phone Number
                                OutlinedTextField(
                                    value = shopPhone,
                                    onValueChange = { shopPhone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }.take(15) },
                                    label = { Text("Shopkeeper Contact Phone *") },
                                    placeholder = { Text("e.g. 9876543210 (Printed on bills)") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("cloud_phone_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Field 4: Email ID
                                OutlinedTextField(
                                    value = profileEmail,
                                    onValueChange = { profileEmail = it },
                                    label = { Text("Email ID *") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("cloud_email_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Button(
                                    onClick = {
                                        val cleanShop = shopName.trim()
                                        val cleanAddr = address.trim()
                                        val cleanPhone = shopPhone.trim()
                                        val cleanMail = profileEmail.trim()

                                        if (cleanShop.isBlank() || cleanAddr.isBlank() || cleanMail.isBlank() || cleanPhone.isBlank()) {
                                            errorMessage = "Please fill in Shop Name, Address, Contact Phone, and Email ID."
                                            return@Button
                                        }
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            try {
                                                userProfileRepo.saveUserProfile(
                                                    UserProfile(
                                                        userId = currentUser!!.uid,
                                                        email = cleanMail,
                                                        shopName = cleanShop,
                                                        address = cleanAddr,
                                                        phone = cleanPhone
                                                    )
                                                )
                                                val existingBiz = app.container.businessRepository.getBusinessProfileOnce()
                                                val localSaved = (existingBiz ?: BusinessProfile(businessName = cleanShop)).copy(
                                                    businessName = cleanShop,
                                                    address = cleanAddr,
                                                    email = cleanMail,
                                                    phone = cleanPhone
                                                )
                                                app.container.businessRepository.saveBusinessProfile(localSaved, completeOnboarding = true)
                                                app.container.preferences.markSetupCompleteForUidSync(currentUser!!.uid)
                                                app.container.cloudSyncRepository.startSyncForUser(currentUser!!.uid)
                                                isLoading = false
                                                onAuthComplete()
                                            } catch (e: Exception) {
                                                isLoading = false
                                                errorMessage = e.localizedMessage ?: "Failed to save profile. Please check connection."
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("save_cloud_profile_btn"),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isLoading
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text("Save Profile & Launch HisabPulse", fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        signOutFromFirebaseAndCredentials(context, scope)
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Switch Account / Sign Out")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Render active legal dialog if opened
    activeLegalDialog?.let { type ->
        LegalDisclosuresDialog(
            type = type,
            onDismiss = { activeLegalDialog = null }
        )
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
