package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.mapmory.shared.presentation.photo.PhotoRecommendationPagingState

/** In-memory draft data retained by the saved navigation entry, including photo references. */
internal class RetainedPhotoFlowState : ViewModel() {
    val paging = mutableStateOf(PhotoRecommendationPagingState())
    val forwardStep = mutableStateOf<String?>(null)
    val forwardPreview = mutableStateOf<String?>(null)
    var resumeSearch = false
}
