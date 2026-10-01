package com.alal.yft.core.media.di

import com.alal.yft.core.media.resolver.DefaultVariantResolver
import com.alal.yft.core.media.resolver.VariantResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaResolverModule {
    @Binds
    @Singleton
    abstract fun bindVariantResolver(implementation: DefaultVariantResolver): VariantResolver
}