package com.mapmory.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.data.auth.*
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.domain.group.GroupDetail
import com.mapmory.shared.domain.group.GroupMemberView
import com.mapmory.shared.domain.group.GroupSummary
import com.mapmory.shared.domain.group.GroupInvitationPreview
import com.mapmory.shared.domain.group.IssuedGroupInvitation
import com.mapmory.shared.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated repository fixtures. Device verification against the development server is separate. */
class GroupLiveNavigationTest {
    @get:Rule val compose = createComposeRule()
    private var createdName: String? = null
    private var issueCalls = 0
    private val member = AccountMember("테스트 계정", MemberProvider.GOOGLE)

    private fun show(developer: Boolean = false) {
        val base = createInMemoryAppContainer()
        val session = object : AccountSession {
            override val state = MutableStateFlow(AccountState(member = member))
            override suspend fun restore() = Result.success(member)
            override suspend fun login(provider: MemberProvider, idToken: String, recoverInvalidSession: Boolean) = Result.success(member)
            override suspend fun resumeLogin() = Result.success(member)
        }
        val container = object : AppContainer by base {
            override val accountSession = session
            override val groupRepository = object : GroupRepository {
                private fun detail(name: String = "API 그룹") = GroupDetail(81, name, listOf(GroupMemberView("테스트 계정", true, true)))
                override suspend fun groups() = Result.success(listOf(GroupSummary(81, "API 그룹", 1, 0)))
                override suspend fun getGroup(groupId: Long) = Result.success(detail())
                override suspend fun createGroup(name: String): Result<GroupDetail> { createdName = name; return Result.success(detail(name)) }
                override suspend fun renameGroup(groupId: Long, name: String) = Result.success(detail(name))
                override suspend fun createInvitation(groupId: Long): Result<IssuedGroupInvitation> {
                    issueCalls++
                    return Result.success(IssuedGroupInvitation("token", "ABC2D3", LocalDateTime(2026, 10, 28, 12, 0)))
                }
                override suspend fun previewInvitation(tokenOrCode: String) = Result.success(GroupInvitationPreview("API 그룹", 1, true))
                override suspend fun acceptInvitation(tokenOrCode: String) = Result.success(detail())
            }
        }
        val info = if (developer) MapmoryDeveloperToolsInfo("test", 1, "fixture", "debug", "https://example.com", "fixture", "35") else null
        compose.setContent { MapmoryApp(container = container, developerToolsInfo = info) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("그룹").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("그룹").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("API 그룹").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun groupTabUsesRepositoryInDeveloperBuildWithoutSampleEntry() {
        show(developer = true)
        compose.onNodeWithText("그룹 앨범 화면 미리보기").assertDoesNotExist()
        compose.onNodeWithText("제주에서의 우리").assertDoesNotExist()
        compose.onNodeWithText("API 그룹").performClick()
        compose.onNodeWithText("테스트 계정 · 나 · 방장").assertExists()
        compose.onNodeWithText("앨범 기능을 준비하고 있어요.").assertExists()
        compose.onNodeWithText("멤버 초대").performClick()
        compose.onNodeWithText("초대 코드 만들기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("ABC2D3").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, issueCalls)
    }

    @Test fun creationSubmitsNameAndShowsReturnedGroup() {
        show()
        compose.onNodeWithText("새 그룹 만들기").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("실제 연결 이름")
        compose.onNodeWithText("그룹 만들기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("실제 연결 이름").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("실제 연결 이름", createdName)
        compose.onNodeWithText("테스트 계정 · 나 · 방장").assertExists()
    }
}
