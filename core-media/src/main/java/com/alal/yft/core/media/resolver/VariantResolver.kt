package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.VariantResolutionResult

interface VariantResolver {
    suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult
}