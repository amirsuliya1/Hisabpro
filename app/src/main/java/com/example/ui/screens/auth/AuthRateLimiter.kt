package com.example.ui.screens.auth

import android.content.Context
import android.content.SharedPreferences

object AuthRateLimiter {

    private const val PREFS_NAME = "hisabpulse_auth_ratelimit"
    private const val COOLDOWN_SECONDS = 60
    private const val MAX_PHONE_REQUESTS = 3
    private const val PHONE_WINDOW_MILLIS = 10 * 60 * 1000L // 10 minutes
    private const val MAX_EMAIL_REQUESTS = 3
    private const val EMAIL_WINDOW_MILLIS = 10 * 60 * 1000L // 10 minutes
    private const val MAX_VERIFY_ATTEMPTS = 5
    private const val VERIFY_LOCKOUT_MILLIS = 3 * 60 * 1000L // 3 minutes

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class RateLimitResult(
        val isAllowed: Boolean,
        val cooldownSecondsRemaining: Int = 0,
        val attemptsRemaining: Int = 0,
        val userFriendlyMessage: String? = null
    )

    fun checkPhoneOtpRateLimit(context: Context, e164Phone: String): RateLimitResult {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val cleanPhone = e164Phone.filter { it.isDigit() || it == '+' }

        val lastSentKey = "phone_last_sent_$cleanPhone"
        val timestampsKey = "phone_timestamps_$cleanPhone"

        // Clean up sliding window timestamps first
        val rawStamps = prefs.getString(timestampsKey, "").orEmpty()
        val validTimestamps = rawStamps.split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { (now - it) < PHONE_WINDOW_MILLIS }

        if (validTimestamps.size >= MAX_PHONE_REQUESTS) {
            val oldest = validTimestamps.minOrNull() ?: now
            val waitMinutes = (((oldest + PHONE_WINDOW_MILLIS) - now) / 60000L).coerceAtLeast(1)
            return RateLimitResult(
                isAllowed = false,
                attemptsRemaining = 0,
                userFriendlyMessage = "Rate limit reached: Maximum $MAX_PHONE_REQUESTS OTP requests per 10 minutes. Please wait $waitMinutes minute(s) or continue with Google."
            )
        }

        val lastSent = prefs.getLong(lastSentKey, 0L)
        val elapsedSec = (now - lastSent) / 1000L
        if (lastSent > 0L && elapsedSec < COOLDOWN_SECONDS) {
            val remainingSec = (COOLDOWN_SECONDS - elapsedSec).toInt().coerceAtLeast(1)
            return RateLimitResult(
                isAllowed = false,
                cooldownSecondsRemaining = remainingSec,
                userFriendlyMessage = "Please wait $remainingSec seconds before requesting another OTP."
            )
        }

        val remainingAttempts = (MAX_PHONE_REQUESTS - validTimestamps.size).coerceAtLeast(1)
        return RateLimitResult(
            isAllowed = true,
            attemptsRemaining = remainingAttempts
        )
    }

    fun recordPhoneOtpSent(context: Context, e164Phone: String) {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val cleanPhone = e164Phone.filter { it.isDigit() || it == '+' }

        val lastSentKey = "phone_last_sent_$cleanPhone"
        val timestampsKey = "phone_timestamps_$cleanPhone"

        val rawStamps = prefs.getString(timestampsKey, "").orEmpty()
        val validTimestamps = rawStamps.split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { (now - it) < PHONE_WINDOW_MILLIS }
            .toMutableList()

        validTimestamps.add(now)

        prefs.edit()
            .putLong(lastSentKey, now)
            .putString(timestampsKey, validTimestamps.joinToString(","))
            .apply()
    }

    fun checkOtpVerifyAttempts(context: Context, verificationId: String): RateLimitResult {
        if (verificationId.isBlank()) return RateLimitResult(isAllowed = true)
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val countKey = "verify_count_$verificationId"
        val lockoutKey = "verify_lockout_$verificationId"

        val lockoutUntil = prefs.getLong(lockoutKey, 0L)
        if (now < lockoutUntil) {
            val remainSec = ((lockoutUntil - now) / 1000L).toInt().coerceAtLeast(1)
            return RateLimitResult(
                isAllowed = false,
                cooldownSecondsRemaining = remainSec,
                userFriendlyMessage = "Too many incorrect OTP attempts. Locked for security. Try again in ${remainSec}s or request a new OTP."
            )
        }

        val attempts = prefs.getInt(countKey, 0)
        val remaining = (MAX_VERIFY_ATTEMPTS - attempts).coerceAtLeast(0)
        return RateLimitResult(
            isAllowed = true,
            attemptsRemaining = remaining
        )
    }

    fun recordOtpVerifyFailure(context: Context, verificationId: String): RateLimitResult {
        if (verificationId.isBlank()) return RateLimitResult(isAllowed = true)
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val countKey = "verify_count_$verificationId"
        val lockoutKey = "verify_lockout_$verificationId"

        val current = prefs.getInt(countKey, 0) + 1
        if (current >= MAX_VERIFY_ATTEMPTS) {
            val lockoutUntil = now + VERIFY_LOCKOUT_MILLIS
            prefs.edit()
                .putInt(countKey, current)
                .putLong(lockoutKey, lockoutUntil)
                .apply()
            return RateLimitResult(
                isAllowed = false,
                cooldownSecondsRemaining = 180,
                userFriendlyMessage = "Too many failed attempts. Account entry paused for 3 minutes for security."
            )
        } else {
            prefs.edit().putInt(countKey, current).apply()
            val remain = MAX_VERIFY_ATTEMPTS - current
            return RateLimitResult(
                isAllowed = true,
                attemptsRemaining = remain,
                userFriendlyMessage = "Incorrect OTP code. $remain attempt(s) remaining before security lockout."
            )
        }
    }

    fun recordOtpVerifySuccess(context: Context, verificationId: String) {
        if (verificationId.isBlank()) return
        val prefs = getPrefs(context)
        prefs.edit()
            .remove("verify_count_$verificationId")
            .remove("verify_lockout_$verificationId")
            .apply()
    }

    fun checkEmailLinkRateLimit(context: Context, email: String): RateLimitResult {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val cleanEmail = email.trim().lowercase()

        val lastSentKey = "email_last_sent_$cleanEmail"
        val timestampsKey = "email_timestamps_$cleanEmail"

        val rawStamps = prefs.getString(timestampsKey, "").orEmpty()
        val validTimestamps = rawStamps.split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { (now - it) < EMAIL_WINDOW_MILLIS }

        if (validTimestamps.size >= MAX_EMAIL_REQUESTS) {
            val oldest = validTimestamps.minOrNull() ?: now
            val waitMinutes = (((oldest + EMAIL_WINDOW_MILLIS) - now) / 60000L).coerceAtLeast(1)
            return RateLimitResult(
                isAllowed = false,
                attemptsRemaining = 0,
                userFriendlyMessage = "Rate limit reached: Maximum $MAX_EMAIL_REQUESTS email links per 10 minutes. Please check your spam folder or wait $waitMinutes minute(s)."
            )
        }

        val lastSent = prefs.getLong(lastSentKey, 0L)
        val elapsedSec = (now - lastSent) / 1000L
        if (lastSent > 0L && elapsedSec < COOLDOWN_SECONDS) {
            val remain = (COOLDOWN_SECONDS - elapsedSec).toInt().coerceAtLeast(1)
            return RateLimitResult(
                isAllowed = false,
                cooldownSecondsRemaining = remain,
                userFriendlyMessage = "Please wait $remain seconds before requesting another email verification link."
            )
        }

        val remaining = (MAX_EMAIL_REQUESTS - validTimestamps.size).coerceAtLeast(1)
        return RateLimitResult(
            isAllowed = true,
            attemptsRemaining = remaining
        )
    }

    fun recordEmailLinkSent(context: Context, email: String) {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val cleanEmail = email.trim().lowercase()

        val lastSentKey = "email_last_sent_$cleanEmail"
        val timestampsKey = "email_timestamps_$cleanEmail"

        val rawStamps = prefs.getString(timestampsKey, "").orEmpty()
        val validTimestamps = rawStamps.split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { (now - it) < EMAIL_WINDOW_MILLIS }
            .toMutableList()

        validTimestamps.add(now)

        prefs.edit()
            .putLong(lastSentKey, now)
            .putString(timestampsKey, validTimestamps.joinToString(","))
            .apply()
    }
}
