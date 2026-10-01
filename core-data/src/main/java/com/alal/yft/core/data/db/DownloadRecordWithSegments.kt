package com.alal.yft.core.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class DownloadRecordWithSegments(
    @Embedded
    val record: DownloadRecordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "download_id",
    )
    val segments: List<DownloadSegmentEntity>,
)