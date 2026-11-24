package com.hakdaar.app

import com.hakdaar.app.core.model.BlockedTargetsConfig
import com.hakdaar.app.domain.safety.SensitiveScreenDetector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SensitiveScreenDetectorTest {

    private lateinit var detector: SensitiveScreenDetector

    @Before
    fun setUp() {
        val config = BlockedTargetsConfig(
            version = 1,
            blockedPackages = listOf("com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user"),
            blockedDomains = listOf("onlinesbi.sbi", "netbanking.hdfcbank.com", "paytm.com"),
            englishSensitiveTerms = listOf("password", "otp", "pin", "upi pin", "cvv", "card number", "login", "captcha"),
            hindiSensitiveTerms = listOf("पासवर्ड", "ओटीपी", "पिन", "यूपीआई पिन", "सीवीवी", "हस्ताक्षर", "भुगतान")
        )
        detector = SensitiveScreenDetector(config)
    }

    @Test
    fun testBlockedPackagesDetection() {
        assertTrue(detector.isBlockedPackage("com.phonepe.app"))
        assertTrue(detector.isBlockedPackage("net.one97.paytm"))
        assertFalse(detector.isBlockedPackage("com.android.chrome"))
        assertFalse(detector.isBlockedPackage("com.hakdaar.app"))
    }

    @Test
    fun testBlockedDomainsDetection() {
        assertTrue(detector.isBlockedDomain("https://onlinesbi.sbi/login.html"))
        assertTrue(detector.isBlockedDomain("https://netbanking.hdfcbank.com/netbanking/"))
        assertFalse(detector.isBlockedDomain("http://10.0.2.2:8080/demo/birth-certificate"))
    }

    @Test
    fun testSensitiveKeywordDetectionEnglish() {
        assertTrue(detector.containsSensitiveKeyword("Enter your Password"))
        assertTrue(detector.containsSensitiveKeyword("Enter 6-digit OTP sent to phone"))
        assertTrue(detector.containsSensitiveKeyword("Your UPI PIN"))
        assertTrue(detector.containsSensitiveKeyword("CVV code"))
        assertTrue(detector.containsSensitiveKeyword("Security Captcha"))
        assertFalse(detector.containsSensitiveKeyword("Child's full name"))
        assertFalse(detector.containsSensitiveKeyword("Date of birth"))
        assertFalse(detector.containsSensitiveKeyword("Place of birth"))
    }

    @Test
    fun testSensitiveKeywordDetectionHindi() {
        assertTrue(detector.containsSensitiveKeyword("कृपया अपना पासवर्ड दर्ज करें"))
        assertTrue(detector.containsSensitiveKeyword("ओटीपी दर्ज करें"))
        assertTrue(detector.containsSensitiveKeyword("यूपीआई पिन डालें"))
        assertFalse(detector.containsSensitiveKeyword("बच्चे का पूरा नाम"))
        assertFalse(detector.containsSensitiveKeyword("जन्म तिथि"))
    }
}
