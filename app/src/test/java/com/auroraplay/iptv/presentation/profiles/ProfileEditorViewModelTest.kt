package com.auroraplay.iptv.presentation.profiles

import com.auroraplay.iptv.core.security.ProfileGuard
import com.auroraplay.iptv.core.util.PinHasher
import com.auroraplay.iptv.domain.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun profile(id: String = "1", isKids: Boolean = false, pinHash: String? = null, name: String = "Perfil") =
        Profile(id = id, name = name, avatarColorHex = "#000", isKids = isKids, pinHash = pinHash)

    private fun viewModel(
        profiles: List<Profile> = emptyList(),
        profileRepository: FakeProfileRepository = FakeProfileRepository(profiles),
        profileGuard: ProfileGuard = ProfileGuard(),
    ) = ProfileEditorViewModel(profileRepository = profileRepository, profileGuard = profileGuard) to profileRepository

    @Test
    fun `loading an unprotected profile populates the form and needs no authorization`() = runTest {
        val (vm, _) = viewModel(profiles = listOf(profile(name = "Ana")))

        vm.load("1")
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Ana", state.name)
        assertFalse(state.authRequired)
    }

    @Test
    fun `loading a protected profile with no prior grant blocks the form behind a challenge`() = runTest {
        val (vm, _) = viewModel(profiles = listOf(profile(isKids = true)))

        vm.load("1")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.authRequired)
    }

    @Test
    fun `loading a protected profile that already has a grant skips the challenge`() = runTest {
        val guard = ProfileGuard()
        guard.grant("1")
        val (vm, _) = viewModel(profiles = listOf(profile(isKids = true)), profileGuard = guard)

        vm.load("1")
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.authRequired)
    }

    @Test
    fun `a correct PIN grants the guard and clears the challenge, a wrong one records an error`() = runTest {
        val guard = ProfileGuard()
        val (vm, _) = viewModel(profiles = listOf(profile(pinHash = PinHasher.hash("1234"))), profileGuard = guard)
        vm.load("1")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.authRequired)

        assertFalse(vm.submitAuthPin("0000"))
        assertEquals("PIN incorreto.", vm.uiState.value.authError)
        assertTrue(vm.uiState.value.authRequired)

        assertTrue(vm.submitAuthPin("1234"))
        assertFalse(vm.uiState.value.authRequired)
        assertTrue(guard.isAuthorized("1"))
    }

    @Test
    fun `enabling the lock on a profile with no saved PIN immediately asks for one`() = runTest {
        val (vm, _) = viewModel()

        vm.toggleLock(true)

        assertTrue(vm.uiState.value.isChangingPin)
    }

    @Test
    fun `save is a no-op when the name is blank`() = runTest {
        val (vm, profileRepository) = viewModel()
        vm.updateName("   ")

        var done = false
        vm.save { done = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(done)
        assertTrue(profileRepository.getProfile("new-0") == null)
    }

    @Test
    fun `creating a new profile adds it and makes it active`() = runTest {
        val (vm, profileRepository) = viewModel()
        vm.updateName("Novo Perfil")

        var done = false
        vm.save { done = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(done)
        val created = profileRepository.getProfile("new-0")
        assertEquals("Novo Perfil", created?.name)
        assertEquals("new-0", profileRepository.getActiveProfileId())
    }

    @Test
    fun `setting a new PIN with mismatched confirmation is rejected before saving`() = runTest {
        val (vm, profileRepository) = viewModel()
        vm.updateName("Novo Perfil")
        vm.toggleLock(true)
        vm.updateNewPin("1234")
        vm.updateConfirmPin("4321")

        var done = false
        vm.save { done = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(done)
        assertEquals("Os PINs não coincidem.", vm.uiState.value.pinError)
        assertNull(profileRepository.getProfile("new-0"))
    }

    @Test
    fun `saving a protected profile without a grant is refused, even bypassing the loader's own challenge`() = runTest {
        val guard = ProfileGuard()
        val (vm, profileRepository) = viewModel(profiles = listOf(profile(isKids = true, name = "Kids")), profileGuard = guard)
        // load() only reads authRequired into the UI state — save() re-checks
        // the guard itself independently, so a screen that skipped gating on
        // authRequired (or a stale UI state) still can't sneak a save through.
        vm.load("1")
        dispatcher.scheduler.advanceUntilIdle()
        vm.updateName("Kids Updated")

        var done = false
        vm.save { done = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(done)
        assertEquals("Kids", profileRepository.getProfile("1")?.name) // untouched
        assertTrue(vm.uiState.value.authRequired)
    }

    @Test
    fun `saving a protected profile with a grant succeeds and spends the grant`() = runTest {
        val guard = ProfileGuard()
        val (vm, profileRepository) = viewModel(profiles = listOf(profile(isKids = true, name = "Kids")), profileGuard = guard)
        vm.load("1")
        dispatcher.scheduler.advanceUntilIdle()
        vm.onDeviceAuthPassed() // e.g. the device-credential challenge succeeded
        vm.updateName("Kids Updated")

        var done = false
        vm.save { done = true }
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(done)
        assertEquals("Kids Updated", profileRepository.getProfile("1")?.name)
        assertFalse(guard.isAuthorized("1")) // single-use — spent by this save
    }
}
