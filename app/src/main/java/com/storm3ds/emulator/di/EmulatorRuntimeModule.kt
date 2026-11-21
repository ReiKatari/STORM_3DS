package com.storm3ds.emulator.di

import android.content.Context
import android.os.Build
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dagger.multibindings.IntoMap
import com.storm3ds.emulator.common.PermissionHandler
import com.storm3ds.emulator.common.camera.BlackDSiCameraSource
import com.storm3ds.emulator.common.camera.DSiCameraSource
import com.storm3ds.emulator.common.romprocessors.RomFileProcessorFactory
import com.storm3ds.emulator.common.runtime.ScreenshotFrameBufferProvider
import com.storm3ds.emulator.common.uridelegates.UriHandler
import com.storm3ds.emulator.domain.model.camera.DSiCameraSourceType
import com.storm3ds.emulator.domain.repositories.SettingsRepository
import com.storm3ds.emulator.domain.services.DSiNandManager
import com.storm3ds.emulator.domain.services.EmulatorManager
import com.storm3ds.emulator.impl.camera.DSiCameraSourceMultiplexer
import com.storm3ds.emulator.impl.camera.PhysicalDSiCameraSource
import com.storm3ds.emulator.impl.camera.StaticImageDSiCameraSource
import com.storm3ds.emulator.impl.emulator.AndroidEmulatorManager
import com.storm3ds.emulator.impl.ShaderCompileTimeStore
import com.storm3ds.emulator.impl.emulator.EmulatorSession
import com.storm3ds.emulator.impl.emulator.LifecycleOwnerProvider
import com.storm3ds.emulator.impl.emulator.SramProvider
import com.storm3ds.emulator.impl.image.BitmapFactoryBitmapLoader
import com.storm3ds.emulator.impl.image.BitmapLoader
import com.storm3ds.emulator.impl.image.ImageDecoderBitmapLoader


@Module
@InstallIn(ActivityRetainedComponent::class)
object EmulatorRuntimeModule {

    @MapKey
    private annotation class DSiCameraSourceKey(val value: DSiCameraSourceType)

    @Provides
    @ActivityRetainedScoped
    fun provideFrameBufferProvider(): ScreenshotFrameBufferProvider {
        return ScreenshotFrameBufferProvider()
    }

    @Provides
    @ActivityRetainedScoped
    fun provideSramProvider(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        uriHandler: UriHandler
    ): SramProvider {
        return SramProvider(context, settingsRepository, uriHandler)
    }

    @Provides
    @ActivityRetainedScoped
    fun provideEmulatorLifecycleOwnerProvider(): LifecycleOwnerProvider {
        return LifecycleOwnerProvider()
    }

    @Provides
    @ActivityRetainedScoped
    fun provideBitmapLoader(@ApplicationContext context: Context): BitmapLoader {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoderBitmapLoader(context)
        } else {
            BitmapFactoryBitmapLoader(context)
        }
    }

    @Provides
    @IntoMap
    @DSiCameraSourceKey(DSiCameraSourceType.BLACK_SCREEN)
    fun provideBlackDSiCameraSource(): DSiCameraSource {
        return BlackDSiCameraSource()
    }

    @Provides
    @IntoMap
    @DSiCameraSourceKey(DSiCameraSourceType.PHYSICAL_CAMERAS)
    fun providePhysicalDSiCameraSource(
        @ApplicationContext context: Context,
        lifecycleOwnerProvider: LifecycleOwnerProvider,
        permissionHandler: PermissionHandler,
    ): DSiCameraSource {
        return PhysicalDSiCameraSource(context, lifecycleOwnerProvider, permissionHandler)
    }

    @Provides
    @IntoMap
    @DSiCameraSourceKey(DSiCameraSourceType.STATIC_IMAGE)
    fun provideStaticImageDSiCameraSource(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        bitmapLoader: BitmapLoader,
    ): DSiCameraSource {
        return StaticImageDSiCameraSource(context, settingsRepository, bitmapLoader)
    }

    @Provides
    @ActivityRetainedScoped
    fun provideCameraManagerMultiplexer(
        settingsRepository: SettingsRepository,
        cameraSources: Map<DSiCameraSourceType, @JvmSuppressWildcards DSiCameraSource>,
    ): DSiCameraSourceMultiplexer {
        return DSiCameraSourceMultiplexer(
            dsiCameraSources = cameraSources,
            settingsRepository = settingsRepository,
        )
    }

    @Provides
    @ActivityRetainedScoped
    fun provideEmulatorManager(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        sramProvider: SramProvider,
        screenshotFrameBufferProvider: ScreenshotFrameBufferProvider,
        romFileProcessorFactory: RomFileProcessorFactory,
        permissionHandler: PermissionHandler,
        cameraManagerMultiplexer: DSiCameraSourceMultiplexer,
        emulatorSession: EmulatorSession,
        dsiNandManager: DSiNandManager,
        shaderCompileTimeStore: ShaderCompileTimeStore,
        configurationDirectoryVerifier: com.storm3ds.emulator.domain.services.ConfigurationDirectoryVerifier,
    ): EmulatorManager {
        return AndroidEmulatorManager(
            context,
            settingsRepository,
            sramProvider,
            screenshotFrameBufferProvider,
            romFileProcessorFactory,
            permissionHandler,
            cameraManagerMultiplexer,
            emulatorSession,
            dsiNandManager,
            shaderCompileTimeStore,
            configurationDirectoryVerifier,
        )
    }

    @Provides
    @ActivityRetainedScoped
    fun provideEmulatorSession(): EmulatorSession {
        return EmulatorSession()
    }
}
