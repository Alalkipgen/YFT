package com.alal.yft.thumbnail

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ThumbnailModule {
    @Binds
    abstract fun bindRemoteThumbnails(loader: RemoteThumbnailLoader): RemoteThumbnails

    @Binds
    abstract fun bindDownloadThumbnails(store: SavedDownloadThumbnails): DownloadThumbnails
}
