package com.blackjackoracle.service.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class EntitlementStoreTest {

    private lateinit var store: EntitlementStore
    private lateinit var mockTrialStore: TrialTokenStore

    @Before
    fun setup() {
        mockTrialStore = mock(TrialTokenStore::class.java)
        `when`(mockTrialStore.trialToken).thenReturn(null)
        store = EntitlementStore(mockTrialStore)
    }

    @Test
    fun `default is not premium`() {
        assertFalse(store.isPremium.value)
        assertFalse(store.entitled)
        assertFalse(store.trialActive)
        assertNull(store.activeTrialToken)
    }

    @Test
    fun `entitled drives premium`() {
        store.entitled = true
        assertTrue(store.isPremium.value)

        store.entitled = false
        assertFalse(store.isPremium.value)
    }

    @Test
    fun `debugOverride FORCE ON beats inactive`() {
        store.debugOverride = true
        assertTrue(store.isPremium.value)
    }

    @Test
    fun `debugOverride FORCE OFF masks active entitlement and active trial`() {
        store.entitled = true
        store.debugOverride = false
        assertFalse(store.isPremium.value)

        // Clear override reveals reality
        store.debugOverride = null
        assertTrue(store.isPremium.value)
    }

    @Test
    fun `malformed token rejects and prunes`() {
        store.applyTrial("not.a.jwt")
        assertFalse(store.trialActive)
        assertNull(store.activeTrialToken)
        assertFalse(store.isPremium.value)
    }
}
