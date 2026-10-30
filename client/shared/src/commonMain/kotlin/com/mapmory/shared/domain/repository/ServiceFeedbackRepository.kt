package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.ServiceFeedbackCategory
import com.mapmory.shared.domain.model.ServiceFeedbackPlatform

interface ServiceFeedbackRepository {
    suspend fun submit(
        category: ServiceFeedbackCategory,
        content: String,
        platform: ServiceFeedbackPlatform,
    ): Result<Unit>
}
