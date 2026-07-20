package com.blackjackoracle.service.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

private class FakeTrialStore(initial: String? = null) : TrialStore {
    var token: String? = initial
    override fun getTrialToken(): String? = token
    override fun setTrialToken(token: String) { this.token = token }
    override fun clearTrialToken() { token = null }
}

/// Builds an unsigned JWT (`header.payload.sig`) with the given `exp` claim.
private fun jwt(expEpochSeconds: Long): String {
    val enc = Base64.getUrlEncoder().withoutPadding()
    val header = enc.encodeToString("""{"alg":"none"}""".toByteArray())
    val payload = enc.encodeToString(
        """{"isActiveTrial":true,"exp":$expEpochSeconds}""".toByteArray(),
    )
    return "$header.$payload.sig"
}

class EntitlementStoreTest {

    private val fixedNow = 1_700_000_000L

    private fun store(trialStore: TrialStore = FakeTrialStore()): EntitlementStore =
        EntitlementStore(trialStore, now = { fixedNow })

    @Test
    fun defaultsToNotPremium() {
        assertFalse(store().isPremium.value)
    }

    @Test
    fun entitledDrivesPremium() {
        val s = store()
        s.entitled = true
        assertTrue(s.isPremium.value)
        s.entitled = false
        assertFalse(s.isPremium.value)
    }

    @Test
    fun forceOnBeatsInactiveEntitlement() {
        val s = store()
        s.debugOverride = true
        assertTrue(s.isPremium.value)
    }

    @Test
    fun forceOffMasksActiveEntitlement() {
        val s = store()
        s.entitled = true
        s.debugOverride = false
        assertFalse(s.isPremium.value)
    }

    @Test
    fun forceOffMasksActiveTrial() {
        val s = store()
        assertTrue(s.applyTrial(jwt(fixedNow + 3600)))
        assertTrue(s.isPremium.value)
        s.debugOverride = false
        assertFalse(s.isPremium.value)
    }

    @Test
    fun clearingOverrideRevealsReality() {
        val s = store()
        s.entitled = true
        s.debugOverride = false
        assertFalse(s.isPremium.value)
        s.debugOverride = null
        assertTrue(s.isPremium.value)
    }

    @Test
    fun activeTrialUnlocksAndExposesToken() {
        val s = store()
        val token = jwt(fixedNow + 3600)
        assertTrue(s.applyTrial(token))
        assertTrue(s.isPremium.value)
        assertEquals(token, s.activeTrialToken)
    }

    @Test
    fun expiredTrialIsRejected() {
        val s = store()
        assertFalse(s.applyTrial(jwt(fixedNow - 1)))
        assertFalse(s.isPremium.value)
        assertNull(s.activeTrialToken)
    }

    @Test
    fun malformedTokenIsRejected() {
        val s = store()
        assertFalse(s.applyTrial("not-a-jwt"))
        assertFalse(s.applyTrial("only.two"))
        assertFalse(s.isPremium.value)
    }

    @Test
    fun persistedActiveTrialIsRestoredOnInit() {
        val token = jwt(fixedNow + 3600)
        val s = store(FakeTrialStore(token))
        assertTrue(s.isPremium.value)
        assertEquals(token, s.activeTrialToken)
    }

    @Test
    fun persistedExpiredTrialIsDroppedOnInit() {
        val fake = FakeTrialStore(jwt(fixedNow - 1))
        val s = store(fake)
        assertFalse(s.isPremium.value)
        assertNull(fake.token)
    }

    @Test
    fun activeTrialTokenIsNullAfterExpiry() {
        val fake = FakeTrialStore()
        var clock = fixedNow
        val s = EntitlementStore(fake, now = { clock })
        assertTrue(s.applyTrial(jwt(fixedNow + 100)))
        assertEquals(jwt(fixedNow + 100), s.activeTrialToken)
        clock = fixedNow + 200
        assertNull(s.activeTrialToken)
    }

    // Tutorial grant — temporary premium for the guided first hand.

    @Test
    fun tutorialGrantUnlocksPremiumForFreeUserAndLeavesNoResidue() {
        val fake = FakeTrialStore()
        val s = store(fake)
        s.setTutorialGrant(true)
        assertTrue(s.isPremium.value)
        s.setTutorialGrant(false)
        // No residue: not premium, nothing persisted, no trial state invented.
        assertFalse(s.isPremium.value)
        assertFalse(s.entitled)
        assertNull(s.activeTrialToken)
        assertNull(fake.token)
    }

    @Test
    fun tutorialGrantWinsOverDebugForceOff() {
        // The tutorial is designed around unlocked features; a lingering
        // debug force-off must not break the demo.
        val s = store()
        s.debugOverride = false
        s.setTutorialGrant(true)
        assertTrue(s.isPremium.value)
        s.setTutorialGrant(false)
        assertFalse(s.isPremium.value)
    }
}
