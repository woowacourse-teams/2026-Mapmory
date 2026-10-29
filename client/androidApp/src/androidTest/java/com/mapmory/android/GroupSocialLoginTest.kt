package com.mapmory.android

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.data.auth.*
import com.mapmory.shared.data.remote.*
import com.mapmory.shared.domain.repository.GroupRepository
import com.mapmory.shared.domain.group.GroupSummary
import com.mapmory.shared.domain.group.GroupDetail
import com.mapmory.shared.domain.group.GroupInvitationPreview
import com.mapmory.shared.domain.group.IssuedGroupInvitation
import com.mapmory.shared.presentation.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated UI fixtures: no real provider account, production API, or user storage is touched. */
class GroupSocialLoginTest {
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<Pair<String, Map<String, String>>>()
    private val analytics = object : MapmoryAnalytics {
        override fun logEvent(name: String, parameters: Map<String, String>) { events += name to parameters }
        override fun setUserProperty(name: String, value: String?) = Unit
    }
    private class FixtureSession : AccountSession {
        override val state = MutableStateFlow(AccountState(member = AccountMember("게스트", MemberProvider.GUEST)))
        var calls = 0
        var failStore = false
        var logoutCalls = 0
        var failLogout = false
        override suspend fun restore() = Result.success(state.value.member!!)
        override suspend fun login(provider: MemberProvider, idToken: String, recoverInvalidSession: Boolean): Result<AccountMember> {
            calls++
            if (failStore) {
                state.value = state.value.copy(pendingLogin = true, stage = "token_store")
                return Result.failure(IllegalStateException("fixture storage failure"))
            }
            return success()
        }
        override suspend fun resumeLogin() = success()
        override suspend fun logout(): Result<Unit> {
            logoutCalls++
            if (failLogout) {
                state.value = state.value.copy(pendingLogout = true, stage = "logout_store")
                return Result.failure(IllegalStateException("fixture deletion failure"))
            }
            state.value = AccountState(member = AccountMember("게스트", MemberProvider.GUEST),
                generation = state.value.generation + 1, stage = "logged_out")
            return Result.success(Unit)
        }
        private fun success(): Result<AccountMember> {
            val member = AccountMember("테스트 계정", MemberProvider.GOOGLE)
            state.value = AccountState(member = member, generation = state.value.generation + 1)
            return Result.success(member)
        }
    }

    private fun show(session: FixtureSession, cancelled: Boolean = false, failList: Boolean = false): AccountLoginController {
        val base = createInMemoryAppContainer()
        val controller = AccountLoginController(session, analytics)
        var listCalls = 0
        val container = object : AppContainer by base {
            override val accountSession = session
            override val accountLoginController = controller
            override val groupRepository = object : GroupRepository {
                override suspend fun createGroup(name: String): Result<GroupDetail> = error("Not used")
                override suspend fun getGroup(groupId: Long): Result<GroupDetail> = error("Not used")
                override suspend fun renameGroup(groupId: Long, name: String): Result<GroupDetail> = error("Not used")
                override suspend fun createInvitation(groupId: Long): Result<IssuedGroupInvitation> = error("Not used")
                override suspend fun previewInvitation(tokenOrCode: String): Result<GroupInvitationPreview> = error("Not used")
                override suspend fun acceptInvitation(tokenOrCode: String): Result<GroupDetail> = error("Not used")
                override suspend fun groups(): Result<List<GroupSummary>> {
                    listCalls++
                    return if (failList && listCalls == 1) Result.failure(IllegalStateException("fixture offline"))
                    else Result.success(listOf(GroupSummary(1, "에뮬레이터 검증 그룹", 2, 0)))
                }
            }
        }
        val launcher = object : SocialLoginLauncher {
            override val providers = listOf(SocialProvider.GOOGLE, SocialProvider.KAKAO)
            override fun launch(provider: SocialProvider, onResult: (SocialLoginResult) -> Unit) {
                onResult(if (cancelled) SocialLoginCancelled else SocialLoginSuccess(SocialCredential(provider, idToken = "fixture-proof")))
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalSocialLoginLauncher provides launcher) {
                MapmoryApp(container = container, analytics = analytics)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("그룹").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Google로 계속하기").assertDoesNotExist()
        compose.onNodeWithText("그룹").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Google로 계속하기").fetchSemanticsNodes().isNotEmpty() }
        return controller
    }

    @Test fun loginStartsInGroupTabAndSuccessfulLoginReturnsToRealList() {
        val session = FixtureSession(); val controller = show(session)
        try {
            screenshot("group-login-gate")
            compose.onNodeWithText("Google로 계속하기").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("에뮬레이터 검증 그룹").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Google로 계속하기").assertDoesNotExist()
            screenshot("group-login-success")
            assertEquals(1, session.calls)
            assertEquals(1, events.count { it.first == "login" })
            assertEquals("group_tab", events.first { it.first == "social_login_viewed" }.second["source"])
            assertEquals("provider_auth", events.first { it.first == "social_login_completed" }.second["stage"])
        } finally { controller.close() }
    }

    @Test fun cancellationKeepsGuestAndDoesNotEmitLoginOrAppError() {
        val session = FixtureSession(); val controller = show(session, cancelled = true)
        try {
            compose.onNodeWithText("Google로 계속하기").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Google로 계속하기").assertIsEnabled()
            assertEquals(0, session.calls)
            assertFalse(events.any { it.first == "login" || it.first == "app_error" })
        } finally { controller.close() }
    }

    @Test fun storageFailureResumesWithoutRepeatingProviderLogin() {
        val session = FixtureSession().apply { failStore = true }; val controller = show(session)
        try {
            compose.onNodeWithText("Google로 계속하기").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("로그인 결과 확인").fetchSemanticsNodes().isNotEmpty() }
            screenshot("group-login-storage-retry")
            assertFalse(events.any { it.first == "login" })
            compose.onNodeWithText("로그인 결과 확인").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("에뮬레이터 검증 그룹").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, session.calls)
            assertEquals(1, events.count { it.first == "login" })
        } finally { controller.close() }
    }

    @Test fun groupListFailureRetriesOnlyListAndPreservesLogin() {
        val session = FixtureSession(); val controller = show(session, failList = true)
        try {
            compose.onNodeWithText("Google로 계속하기").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("그룹 새로고침").fetchSemanticsNodes().isNotEmpty() }
            screenshot("group-list-retry")
            compose.onNodeWithText("그룹 새로고침").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("에뮬레이터 검증 그룹").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, session.calls)
            assertEquals(1, events.count { it.first == "login" })
        } finally { controller.close() }
    }

    @Test fun logoutCanBeCancelledThenClearsAccountAndReturnsToMap() {
        val session = FixtureSession(); val controller = show(session)
        try {
            compose.onNodeWithText("Google로 계속하기").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("에뮬레이터 검증 그룹").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("설정").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("로그아웃").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("로그아웃").performScrollTo().performClick()
            compose.onNodeWithText("취소").performClick()
            assertEquals(0, session.logoutCalls)
            compose.onNodeWithText("로그아웃").performClick()
            screenshot("logout-confirmation")
            compose.onAllNodesWithText("로그아웃").onLast().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("나의 세계 지도").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, session.logoutCalls)
            assertEquals(1, events.count { it.first == "logout" })
            compose.onNodeWithText("그룹").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Google로 계속하기").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("에뮬레이터 검증 그룹").assertDoesNotExist()
        } finally { controller.close() }
    }

    @Test fun logoutFailureRequiresRetryBeforeUsingAnotherAccount() {
        val session = FixtureSession(); val controller = show(session)
        try {
            compose.onNodeWithText("Google로 계속하기").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("에뮬레이터 검증 그룹").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("설정").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("로그아웃").fetchSemanticsNodes().isNotEmpty() }
            session.failLogout = true
            compose.onNodeWithText("로그아웃").performScrollTo().performClick()
            compose.onAllNodesWithText("로그아웃").onLast().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("로그아웃 다시 시도").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(events.any { it.first == "logout" })
            screenshot("logout-retry")
            session.failLogout = false
            compose.onNodeWithText("로그아웃 다시 시도").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("나의 세계 지도").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(2, session.logoutCalls)
            assertEquals(1, events.count { it.first == "logout" })
        } finally { controller.close() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idleness does not wait for the platform Dialog window animation.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "social-login-verification").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
