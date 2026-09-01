package com.mapmory.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.presentation.photo.PhotoLibraryActions
import com.mapmory.shared.presentation.photo.PhotoRecommendationPage
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.triprecord.screen.TripRecordEditorScreen
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoRecommendationFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `선택한_장소의_추천_사진을_확인하고_추가할_수_있다`() {
        val seoul = province()
        val gangnam = district(parentId = seoul.id)
        val recommendedPhoto = SelectedPhoto(
            id = "gangnam-photo",
            displayName = "gangnam.jpg",
            previewBytes = null,
            latitude = 37.4979,
            longitude = 127.0276,
            capturedAt = "2026.08.21",
        )
        val laterLoadedPhoto = recommendedPhoto.copy(
            id = "gangnam-photo-2",
            displayName = "gangnam-2.jpg",
        )
        var requestedLocation: Location? = null
        var requestedParentName: String? = null
        var preparedPhotos = emptyList<SelectedPhoto>()
        var addedPhotos = emptyList<SelectedPhoto>()

        composeRule.setContent {
            TripRecordEditorScreen(
                uiState = TripRecordEditorUiState(selectedLocation = gangnam),
                locations = listOf(seoul, gangnam),
                onLocationSelected = {},
                onTitleChanged = {},
                onContentChanged = {},
                onStartDateChanged = {},
                onEndDateChanged = {},
                onPhotosAdded = { photos -> addedPhotos = photos },
                onSaveClick = {},
                onBackClick = {},
                photoLibraryActionsFactory = { _, onRecommended, _, _, _, _ ->
                    PhotoLibraryActions(
                        pickFromGallery = {},
                        recommendForLocation = { location, parentName ->
                            requestedLocation = location
                            requestedParentName = parentName
                            onRecommended(
                                PhotoRecommendationPage(
                                    generation = 1,
                                    photos = listOf(recommendedPhoto),
                                    hasMore = true,
                                ),
                            )
                            onRecommended(
                                PhotoRecommendationPage(
                                    generation = 1,
                                    photos = listOf(recommendedPhoto, laterLoadedPhoto),
                                    hasMore = false,
                                ),
                            )
                        },
                        prepareForAdding = { photos, onReady ->
                            preparedPhotos = photos
                            onReady(photos)
                        },
                    )
                },
            )
        }

        composeRule.onNodeWithText("위치 기반 사진\n불러오기").performClick()

        composeRule.onNodeWithText("이 장소에서 찍은 사진").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(gangnam, requestedLocation)
            assertEquals(seoul.name, requestedParentName)
        }

        composeRule.onNodeWithText("선택한 사진 추가").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(recommendedPhoto, laterLoadedPhoto), preparedPhotos)
            assertEquals(listOf(recommendedPhoto, laterLoadedPhoto), addedPhotos)
        }
    }

    @Test
    fun `추천_사진은_세_열_그리드로_배치된다`() {
        val seoul = province()
        val gangnam = district(parentId = seoul.id)
        val recommendedPhotos = (1..24).map { id ->
            SelectedPhoto(
                id = "photo-$id",
                displayName = "photo-$id.jpg",
                previewBytes = null,
            )
        }

        composeRule.setContent {
            TripRecordEditorScreen(
                uiState = TripRecordEditorUiState(selectedLocation = gangnam),
                locations = listOf(seoul, gangnam),
                onLocationSelected = {},
                onTitleChanged = {},
                onContentChanged = {},
                onStartDateChanged = {},
                onEndDateChanged = {},
                onSaveClick = {},
                onBackClick = {},
                photoLibraryActionsFactory = { _, onRecommended, _, _, _, _ ->
                    PhotoLibraryActions(
                        pickFromGallery = {},
                        recommendForLocation = { _, _ ->
                            onRecommended(
                                PhotoRecommendationPage(
                                    generation = 1,
                                    photos = recommendedPhotos,
                                    hasMore = true,
                                ),
                            )
                        },
                    )
                },
            )
        }

        composeRule.onNodeWithText("위치 기반 사진\n불러오기").performClick()
        composeRule.onNodeWithTag("photo-recommendation-grid").assertIsDisplayed()

        val first = composeRule
            .onNodeWithTag("photo-recommendation-item-photo-1")
            .fetchSemanticsNode()
            .boundsInRoot
        val second = composeRule
            .onNodeWithTag("photo-recommendation-item-photo-2")
            .fetchSemanticsNode()
            .boundsInRoot
        val third = composeRule
            .onNodeWithTag("photo-recommendation-item-photo-3")
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(first.top, second.top, 0.5f)
        assertEquals(first.top, third.top, 0.5f)
        assertTrue(first.left < second.left && second.left < third.left)

        composeRule.onNodeWithTag("photo-recommendation-grid").performScrollToIndex(3)
        val fourth = composeRule
            .onNodeWithTag("photo-recommendation-item-photo-4")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(fourth.top > first.top)
    }

    @Test
    fun `추천_사진_그리드_끝에_도달하면_다음_페이지를_불러온다`() {
        val seoul = province()
        val gangnam = district(parentId = seoul.id)
        val firstPage = (1..24).map { id -> photo(id) }
        val nextPagePhoto = photo(25)
        var nextPageCalls = 0

        composeRule.setContent {
            TripRecordEditorScreen(
                uiState = TripRecordEditorUiState(selectedLocation = gangnam),
                locations = listOf(seoul, gangnam),
                onLocationSelected = {},
                onTitleChanged = {},
                onContentChanged = {},
                onStartDateChanged = {},
                onEndDateChanged = {},
                onSaveClick = {},
                onBackClick = {},
                photoLibraryActionsFactory = { _, onRecommended, _, _, _, _ ->
                    PhotoLibraryActions(
                        pickFromGallery = {},
                        recommendForLocation = { _, _ ->
                            onRecommended(
                                PhotoRecommendationPage(
                                    generation = 1,
                                    photos = firstPage,
                                    hasMore = true,
                                ),
                            )
                        },
                        loadNextRecommendationPage = {
                            nextPageCalls += 1
                            onRecommended(
                                PhotoRecommendationPage(
                                    generation = 1,
                                    photos = listOf(nextPagePhoto),
                                    hasMore = false,
                                ),
                            )
                        },
                    )
                },
            )
        }

        composeRule.onNodeWithText("위치 기반 사진\n불러오기").performClick()
        val grid = composeRule.onNodeWithTag("photo-recommendation-grid")
        grid.performScrollToIndex(23)

        composeRule.runOnIdle { assertEquals(1, nextPageCalls) }
        grid.performScrollToIndex(24)
        composeRule.onNodeWithTag("photo-recommendation-item-photo-25").assertIsDisplayed()
    }

    @Test
    fun `장소를_선택하지_않으면_사진_라이브러리를_호출하지_않고_안내를_표시한다`() {
        var recommendationCalls = 0

        composeRule.setContent {
            TripRecordEditorScreen(
                uiState = TripRecordEditorUiState(),
                locations = listOf(province()),
                onLocationSelected = {},
                onTitleChanged = {},
                onContentChanged = {},
                onStartDateChanged = {},
                onEndDateChanged = {},
                onSaveClick = {},
                onBackClick = {},
                photoLibraryActionsFactory = { _, _, _, _, _, _ ->
                    PhotoLibraryActions(
                        pickFromGallery = {},
                        recommendForLocation = { _, _ -> recommendationCalls += 1 },
                    )
                },
            )
        }

        composeRule.onNodeWithText("위치 기반 사진\n불러오기").performClick()

        composeRule.onNodeWithText("사진을 추천받으려면 장소를 먼저 선택해 주세요.")
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, recommendationCalls) }
    }

    @Test
    fun `기록_여행지는_국내_시도를_제외하고_시군구만_선택할_수_있다`() {
        val seoul = province()
        val gangnam = district(parentId = seoul.id)
        var selectedLocation: Location? = null

        composeRule.setContent {
            TripRecordEditorScreen(
                uiState = TripRecordEditorUiState(),
                locations = listOf(seoul, gangnam),
                onLocationSelected = { selectedLocation = it },
                onTitleChanged = {},
                onContentChanged = {},
                onStartDateChanged = {},
                onEndDateChanged = {},
                onSaveClick = {},
                onBackClick = {},
                photoLibraryActionsFactory = { _, _, _, _, _, _ ->
                    PhotoLibraryActions(
                        pickFromGallery = {},
                        recommendForLocation = { _, _ -> },
                    )
                },
            )
        }

        composeRule.onNodeWithText("여행 장소를 선택해 주세요").performClick()

        assertEquals(0, composeRule.onAllNodesWithText("서울특별시").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("장소명 또는 코드 검색").performTextInput("서울특별시")
        composeRule.onNodeWithText("강남구").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(gangnam, selectedLocation) }
    }

    @Test
    fun `사진첩을_불러오는_동안_위치_기반_추천_버튼은_로딩으로_바뀌지_않는다`() {
        composeRule.setContent {
            TripRecordEditorScreen(
                uiState = TripRecordEditorUiState(
                    selectedLocation = district(parentId = province().id),
                    isPhotoLoading = true,
                ),
                locations = listOf(province(), district(parentId = province().id)),
                onLocationSelected = {},
                onTitleChanged = {},
                onContentChanged = {},
                onStartDateChanged = {},
                onEndDateChanged = {},
                onSaveClick = {},
                onBackClick = {},
                photoLibraryActionsFactory = { _, _, _, _, _, _ ->
                    PhotoLibraryActions(
                        pickFromGallery = {},
                        recommendForLocation = { _, _ -> },
                    )
                },
            )
        }

        composeRule.onNodeWithText("사진첩").assertIsDisplayed()
        composeRule.onNodeWithText("위치 기반 사진\n불러오기").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("불러오는 중").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `위치_기반_사진_추천_중_버튼을_누르면_추천을_취소한다`() {
        var onRecommendationLoadingChanged: ((Boolean) -> Unit)? = null
        var cancelCalls = 0

        composeRule.setContent {
            val seoul = province()
            val gangnam = district(parentId = seoul.id)
            TripRecordEditorScreen(
                uiState = TripRecordEditorUiState(
                    selectedLocation = gangnam,
                    isPhotoLoading = true,
                ),
                locations = listOf(seoul, gangnam),
                onLocationSelected = {},
                onTitleChanged = {},
                onContentChanged = {},
                onStartDateChanged = {},
                onEndDateChanged = {},
                onSaveClick = {},
                onBackClick = {},
                photoLibraryActionsFactory = { _, _, _, _, _, recommendationLoadingChanged ->
                    onRecommendationLoadingChanged = recommendationLoadingChanged
                    PhotoLibraryActions(
                        pickFromGallery = {},
                        recommendForLocation = { _, _ -> },
                        cancelRecommendation = { cancelCalls += 1 },
                    )
                },
            )
        }

        composeRule.runOnIdle {
            onRecommendationLoadingChanged?.invoke(true)
        }
        composeRule.onNodeWithText("중단").performClick()

        composeRule.runOnIdle { assertEquals(1, cancelCalls) }
    }

    private fun province() = Location(
        id = 1L,
        countryId = 1L,
        parentId = null,
        regionCode = "KR-11",
        name = "서울특별시",
        type = LocationType.PROVINCE,
    )

    private fun district(parentId: Long) = Location(
        id = 2L,
        countryId = 1L,
        parentId = parentId,
        regionCode = "11680",
        name = "강남구",
        type = LocationType.DISTRICT,
    )

    private fun photo(id: Int) = SelectedPhoto(
        id = "photo-$id",
        displayName = "photo-$id.jpg",
        previewBytes = null,
    )
}
