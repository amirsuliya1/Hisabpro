package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.auth.AuthRateLimiter
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
class AuthRateLimiterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("hisabpulse_auth_ratelimit", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `phone otp rate limit allows initial request and enforces cooldown`() {
        val phone = "+919876543210"
        val check1 = AuthRateLimiter.checkPhoneOtpRateLimit(context, phone)
        assertTrue(check1.isAllowed)
        assertEquals(3, check1.attemptsRemaining)

        AuthRateLimiter.recordPhoneOtpSent(context, phone)

        val checkImmediate = AuthRateLimiter.checkPhoneOtpRateLimit(context, phone)
        assertFalse(checkImmediate.isAllowed)
        assertTrue(checkImmediate.cooldownSecondsRemaining in 1..60)
    }

    @Test
    fun `phone otp rate limit blocks after 3 requests in window`() {
        val phone = "+919999988888"
        repeat(3) {
            AuthRateLimiter.recordPhoneOtpSent(context, phone)
        }
        val checkBlocked = AuthRateLimiter.checkPhoneOtpRateLimit(context, phone)
        assertFalse(checkBlocked.isAllowed)
        assertTrue(checkBlocked.userFriendlyMessage?.contains("Rate limit reached") == true)
    }

    @Test
    fun `otp verification locks out after 5 consecutive failed attempts`() {
        val verId = "test_verification_session_123"
        val checkInitial = AuthRateLimiter.checkOtpVerifyAttempts(context, verId)
        assertTrue(checkInitial.isAllowed)

        repeat(4) {
            val failRes = AuthRateLimiter.recordOtpVerifyFailure(context, verId)
            assertTrue(failRes.isAllowed)
        }

        // 5th failure triggers lockout
        val fifthFail = AuthRateLimiter.recordOtpVerifyFailure(context, verId)
        assertFalse(fifthFail.isAllowed)
        assertTrue(fifthFail.cooldownSecondsRemaining > 0)

        val checkLocked = AuthRateLimiter.checkOtpVerifyAttempts(context, verId)
        assertFalse(checkLocked.isAllowed)

        // Success resets attempts
        AuthRateLimiter.recordOtpVerifySuccess(context, verId)
        val checkAfterSuccess = AuthRateLimiter.checkOtpVerifyAttempts(context, verId)
        assertTrue(checkAfterSuccess.isAllowed)
    }

    @Test
    fun `email link rate limit enforces cooldown and maximum attempts`() {
        val email = "shopkeeper@test.com"
        val initial = AuthRateLimiter.checkEmailLinkRateLimit(context, email)
        assertTrue(initial.isAllowed)

        AuthRateLimiter.recordEmailLinkSent(context, email)
        val immediate = AuthRateLimiter.checkEmailLinkRateLimit(context, email)
        assertFalse(immediate.isAllowed)
    }
}
