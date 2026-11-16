package com.example.core

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityManager {
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(bytes: ByteArray): String {
        val sb = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            sb.append(B64_ALPHABET[b0 shr 2])
            sb.append(B64_ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) (b1 shr 4) else 0)])
            sb.append(if (b1 >= 0) B64_ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 >= 0) (b2 shr 6) else 0)] else '=')
            sb.append(if (b2 >= 0) B64_ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    private fun decodeBase64(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in text) {
            if (ch == '=' || ch.isWhitespace()) continue
            val val6 = B64_ALPHABET.indexOf(ch)
            if (val6 < 0) continue
            buffer = (buffer shl 6) or val6
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return encodeBase64(bytes)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val salt = decodeBase64(saltBase64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return encodeBase64(hash)
    }

    fun verifyPin(pin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        if (saltBase64.isBlank() || expectedHashBase64.isBlank()) return false
        val actual = hashPin(pin, saltBase64)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHashBase64.toByteArray(Charsets.UTF_8)
        )
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encryptAesGcm(plainBytes: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainBytes)
        // Envelope format: MAGIC(4) + SALT(16) + IV(12) + CIPHERTEXT
        val magic = "HPBK".toByteArray(Charsets.US_ASCII)
        return magic + salt + iv + cipherText
    }

    fun decryptAesGcm(encryptedBytes: ByteArray, passphrase: String): ByteArray {
        require(encryptedBytes.size > 32) { "Invalid encrypted backup file" }
        val magic = String(encryptedBytes.copyOfRange(0, 4), Charsets.US_ASCII)
        require(magic == "HPBK") { "Unrecognized encrypted backup header" }
        val salt = encryptedBytes.copyOfRange(4, 20)
        val iv = encryptedBytes.copyOfRange(20, 32)
        val cipherText = encryptedBytes.copyOfRange(32, encryptedBytes.size)
        val key = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun isEncryptedBackup(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "HPBK"
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val effectivePass = if (passphrase.isBlank()) "hisabpulse-default-offline-key" else passphrase
        val spec = PBEKeySpec(effectivePass.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
