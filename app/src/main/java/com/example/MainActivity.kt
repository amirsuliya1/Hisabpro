package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.example.ui.navigation.HisabPulseAppShell
import com.example.ui.screens.auth.FirebaseAuthScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : FragmentActivity() {
    private var incomingAuthLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingAuthLink = intent?.data?.toString()
        val app = applicationContext as HisabPulseApp

        setContent {
            val initialUser = remember { Firebase.auth.currentUser }
            var currentUser by remember { mutableStateOf(initialUser) }
            var profileReady by remember {
                mutableStateOf(
                    initialUser != null &&
                        app.container.preferences.hasCompletedSetupForUidSync(initialUser.uid)
                )
            }

            DisposableEffect(Unit) {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    val nextUser = auth.currentUser
                    if (nextUser == null) {
                        profileReady = false
                        app.container.cloudSyncRepository.stopSync()
                    } else {
                        app.container.cloudSyncRepository.startSyncForUser(nextUser.uid)
                        if (app.container.preferences.hasCompletedSetupForUidSync(nextUser.uid)) {
                            profileReady = true
                        }
                    }
                    currentUser = nextUser
                }
                Firebase.auth.addAuthStateListener(listener)
                onDispose { Firebase.auth.removeAuthStateListener(listener) }
            }

            if (currentUser == null || !profileReady) {
                FirebaseAuthScreen(
                    incomingEmailLink = incomingAuthLink,
                    onConsumeEmailLink = { incomingAuthLink = null },
                    onAuthComplete = {
                        currentUser?.uid?.let { uid ->
                            app.container.preferences.markSetupCompleteForUidSync(uid)
                            app.container.cloudSyncRepository.startSyncForUser(uid)
                        }
                        profileReady = true
                    }
                )
            } else {
                key(currentUser!!.uid) {
                    HisabPulseAppShell()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { link ->
            incomingAuthLink = link
        }
    }
}
