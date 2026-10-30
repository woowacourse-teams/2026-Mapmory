package com.mapmory.shared.presentation.triprecord.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class NewRecordBackActionTest {
    @Test
    fun `saving ignores every back request`() {
        assertEquals(
            NewRecordBackAction.IGNORE,
            newRecordBackAction(
                step = NewRecordFlowStep.PHOTO_PICKER,
                isSaving = true,
                hasOpenPhotoPreview = false,
                isPhotoLoading = false,
            ),
        )
    }

    @Test
    fun `photo preview closes before leaving the picker`() {
        assertEquals(
            NewRecordBackAction.CLOSE_PREVIEW,
            newRecordBackAction(
                step = NewRecordFlowStep.PHOTO_PICKER,
                isSaving = false,
                hasOpenPhotoPreview = true,
                isPhotoLoading = false,
            ),
        )
    }

    @Test
    fun `loading and picker steps require their own confirmation`() {
        assertEquals(
            NewRecordBackAction.CONFIRM_PHOTO_LOADING,
            newRecordBackAction(
                step = NewRecordFlowStep.PHOTO_LOADING,
                isSaving = false,
                hasOpenPhotoPreview = false,
                isPhotoLoading = true,
            ),
        )
        assertEquals(
            NewRecordBackAction.CONFIRM_PHOTO_PICKER,
            newRecordBackAction(
                step = NewRecordFlowStep.PHOTO_PICKER,
                isSaving = false,
                hasOpenPhotoPreview = false,
                isPhotoLoading = false,
            ),
        )
    }

    @Test
    fun `location and completed loading leave the current flow`() {
        assertEquals(
            NewRecordBackAction.EXIT_FLOW,
            newRecordBackAction(
                step = NewRecordFlowStep.LOCATION,
                isSaving = false,
                hasOpenPhotoPreview = false,
                isPhotoLoading = false,
            ),
        )
        assertEquals(
            NewRecordBackAction.EXIT_FLOW,
            newRecordBackAction(
                step = NewRecordFlowStep.PHOTO_LOADING,
                isSaving = false,
                hasOpenPhotoPreview = false,
                isPhotoLoading = false,
            ),
        )
    }
}
