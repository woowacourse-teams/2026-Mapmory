package com.mapmory.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.MapmoryNavigation
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.data.auth.AccountMember
import com.mapmory.shared.data.auth.AccountSession
import com.mapmory.shared.data.auth.AccountState
import com.mapmory.shared.data.auth.MemberProvider
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMediaDraft
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** In-memory account and record fixtures; no provider, server, or device account is used. */
class NotificationAccountRestoreTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun coldNotificationContinuesAfterMemberRestoreWithoutAnAccountGenerationChange() {
        val session = RestoringSession()
        val (container, recordId) = recordFixture(session)
        val navigation = MapmoryNavigation().apply {
            openRecordNotification(recordId, preserveBackStack = false)
        }

        compose.setContent { MapmoryApp(container = container, navigation = navigation) }
        compose.waitUntil(10_000) { session.restoreStarted.isCompleted }
        compose.runOnIdle {
            assertEquals(0L, session.state.value.generation)
            assertTrue(navigation.hasPendingNotification())
            session.allowRestore.complete(Unit)
        }

        waitForDetail()
        compose.runOnIdle {
            assertEquals(MemberProvider.GOOGLE, session.state.value.member?.provider)
            assertEquals(0L, session.state.value.generation)
            assertFalse(navigation.hasPendingNotification())
        }
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.onNodeWithText("총 1개").assertIsDisplayed()
        compose.onNodeWithText(RecordContent).assertDoesNotExist()
    }

    @Test
    fun warmNotificationReturnsToTheExistingSettingsScreen() {
        val session = RestoringSession(restored = true)
        val (container, recordId) = recordFixture(session)
        val navigation = MapmoryNavigation()
        compose.setContent { MapmoryApp(container = container, navigation = navigation) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("설정").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("설정").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("화면 테마").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("화면 테마").assertIsDisplayed()

        compose.runOnIdle { navigation.openRecordNotification(recordId, preserveBackStack = true) }
        waitForDetail()
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        compose.onNodeWithText("화면 테마").assertIsDisplayed()
        compose.onNodeWithText(RecordContent).assertDoesNotExist()
    }

    private fun waitForDetail() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(RecordContent).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(RecordContent).assertIsDisplayed()
    }

    private fun recordFixture(session: AccountSession): Pair<AppContainer, Long> {
        val base = createInMemoryAppContainer()
        val photo = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(Color.RED)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                bitmap.recycle()
                output.toByteArray()
            }
        }
        val record = runBlocking {
            base.tripRecordRepository.createTripRecord(
                TripRecordDraft(
                    locationId = base.regionCatalog.requireByCode("11680").id,
                    startDate = "2026-08-24",
                    mediaObjectKeys = listOf("notification-fixture.png"),
                    title = "알림 복원 테스트",
                    content = RecordContent,
                    localMedia = listOf(
                        TripRecordMediaDraft(
                            objectKey = "notification-fixture.png",
                            sortOrder = 0,
                            previewBytes = photo,
                            originalBytes = photo,
                        ),
                    ),
                ),
            ).getOrThrow()
        }
        return object : AppContainer by base {
            override val accountSession = session
        } to record.id
    }

    private class RestoringSession(restored: Boolean = false) : AccountSession {
        override val state = MutableStateFlow(AccountState(member = Member.takeIf { restored }))
        val restoreStarted = CompletableDeferred<Unit>()
        val allowRestore = CompletableDeferred<Unit>().apply { if (restored) complete(Unit) }

        override suspend fun restore(): Result<AccountMember> {
            restoreStarted.complete(Unit)
            allowRestore.await()
            // A normal /me restore changes the member, not the account generation.
            state.value = state.value.copy(member = Member)
            return Result.success(Member)
        }

        override suspend fun login(
            provider: MemberProvider,
            idToken: String,
            recoverInvalidSession: Boolean,
        ): Result<AccountMember> = error("Provider login is not used by this fixture")

        override suspend fun resumeLogin(): Result<AccountMember> = error("No pending login")
    }

    private companion object {
        val Member = AccountMember("알림 복원 계정", MemberProvider.GOOGLE)
        const val RecordContent = "복원이 끝나면 이 기록의 사진과 내용을 보여줘요."
    }
}
