package dev.abdullah.openlock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Adapted from OpenLock's native PBKDF2 verifier (MIT).
object PinVerifier {
    private const val ITERATIONS = 120_000
    fun newSalt(): String = Base64.getEncoder().encodeToString(
        ByteArray(16).also { SecureRandom().nextBytes(it) }
    )
    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256)
        return try {
            Base64.getEncoder().encodeToString(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            )
        } finally { spec.clearPassword() }
    }
    fun verify(pin: String, salt: String, expected: String): Boolean = runCatching {
        MessageDigest.isEqual(Base64.getDecoder().decode(hash(pin, salt)), Base64.getDecoder().decode(expected))
    }.getOrDefault(false)
    fun valid(pin: String): Boolean = pin.length in 6..12 && pin.all { it in '0'..'9' }
}
