package com.srmfood.gag.feature.auth

import com.srmfood.gag.domain.model.UserRole
import com.srmfood.gag.domain.repository.AuthRepository
import com.srmfood.gag.domain.repository.ThemeMode
import com.srmfood.gag.domain.repository.UserPreferencesRepository
import com.srmfood.gag.domain.usecase.auth.GetUserRoleUseCase
import com.srmfood.gag.domain.usecase.auth.IsLoggedInUseCase
import com.srmfood.gag.feature.auth.splash.SplashViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakePrefs(initial: Boolean, val broken: Boolean = false) : UserPreferencesRepository {
        val done = MutableStateFlow(initial)
        override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
        override val notificationsEnabled: Flow<Boolean> = MutableStateFlow(true)
        override val onboardingComplete: Flow<Boolean> = if (broken) flow { throw java.io.IOException("datastore") } else done
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setNotificationsEnabled(enabled: Boolean) {}
        override suspend fun setOnboardingComplete() { done.value = true }
    }

    private fun auth(loggedIn: Boolean, role: UserRole? = null): AuthRepository {
        val base: AuthRepository = org.mockito.kotlin.mock()
        return object : AuthRepository by base {
            override suspend fun isLoggedIn() = loggedIn
            override suspend fun getUserRole() = role
        }
    }

    private fun vm(prefs: UserPreferencesRepository, a: AuthRepository) = SplashViewModel(IsLoggedInUseCase(a), GetUserRoleUseCase(a), prefs)

    @Test fun `first launch shows onboarding`() = runTest(dispatcher) {
        val v = vm(FakePrefs(false), auth(false)); advanceUntilIdle()
        assertTrue(v.splashState.value.isReady); assertFalse(v.splashState.value.onboardingComplete)
    }

    @Test fun `completing onboarding persists across a restart`() = runTest(dispatcher) {
        val prefs = FakePrefs(false)
        val first = vm(prefs, auth(false)); advanceUntilIdle()
        var navigated = false
        first.completeOnboarding { navigated = true }; advanceUntilIdle()
        assertTrue(navigated); assertTrue(prefs.done.value)
        val restarted = vm(prefs, auth(false)); advanceUntilIdle()      // new process, same device preference
        assertTrue(restarted.splashState.value.onboardingComplete)
        assertFalse(restarted.splashState.value.isLoggedIn)
    }

    @Test fun `logging out or switching account does not replay onboarding`() = runTest(dispatcher) {
        val prefs = FakePrefs(true)
        val v = vm(prefs, auth(false)); advanceUntilIdle()               // signed out, same device
        assertTrue(v.splashState.value.onboardingComplete)
        val other = vm(prefs, auth(true, UserRole.VENDOR)); advanceUntilIdle()   // different account on the device
        assertTrue(other.splashState.value.onboardingComplete); assertEquals(UserRole.VENDOR, other.splashState.value.userRole)
    }

    @Test fun `an already signed-in user from before this flag existed skips onboarding and it is remembered`() = runTest(dispatcher) {
        val prefs = FakePrefs(false)
        val v = vm(prefs, auth(true, UserRole.ADMIN)); advanceUntilIdle()
        assertTrue(v.splashState.value.onboardingComplete); assertTrue(prefs.done.value)
    }

    @Test fun `an unreadable preference never traps the user in onboarding`() = runTest(dispatcher) {
        val v = vm(FakePrefs(false, broken = true), auth(false)); advanceUntilIdle()
        assertTrue(v.splashState.value.onboardingComplete)
    }
}
