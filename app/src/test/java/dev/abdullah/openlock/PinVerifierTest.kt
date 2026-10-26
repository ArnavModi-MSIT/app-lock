package dev.abdullah.openlock

import org.junit.Assert.*
import org.junit.Test

class PinVerifierTest {
    @Test fun correctPinWorksAndWrongPinFails() {
        val salt = PinVerifier.newSalt()
        val hash = PinVerifier.hash("123456", salt)
        assertTrue(PinVerifier.verify("123456", salt, hash))
        assertFalse(PinVerifier.verify("123457", salt, hash))
    }
    @Test fun differentSaltsProduceDifferentVerifiers() {
        val first = PinVerifier.newSalt(); val second = PinVerifier.newSalt()
        assertNotEquals(first, second)
        assertNotEquals(PinVerifier.hash("123456", first), PinVerifier.hash("123456", second))
    }
    @Test fun damagedVerifierDoesNotUnlock() { assertFalse(PinVerifier.verify("123456", "bad!", "bad!")) }
    @Test fun pinMustBeSixToTwelveDigits() {
        assertTrue(PinVerifier.valid("123456")); assertTrue(PinVerifier.valid("000000000000"))
        assertFalse(PinVerifier.valid("12345")); assertFalse(PinVerifier.valid("1234567890123"))
        assertFalse(PinVerifier.valid("12345a"))
    }
}
