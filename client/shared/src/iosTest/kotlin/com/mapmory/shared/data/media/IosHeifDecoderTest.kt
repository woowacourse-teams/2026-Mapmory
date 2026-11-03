package com.mapmory.shared.data.media

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.maxBitmapSize
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosHeifDecoderTest {
    @Test
    fun configuredAppLoaderDisplaysOriginalHeicWithRotatedPixels() = runBlocking {
        configureIosPhotoImageLoader()
        val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
        val result = assertIs<SuccessResult>(load(loader, IosPhotoFixtures.rotatedHeic))
        assertEquals(900, result.image.width)
        assertEquals(1600, result.image.height)
        val bitmap = result.image.toBitmap()
        val top = bitmap.getColor(450, 100)
        val bottom = bitmap.getColor(450, 1500)
        assertTrue(((top shr 16) and 255) > 200, "Top half must be red after EXIF rotation")
        assertTrue((bottom and 255) > 200, "Bottom half must be blue after EXIF rotation")
    }

    @Test
    fun appLoaderDisplaysCachedOriginalWithoutFilenameExtension() = runBlocking {
        configureIosPhotoImageLoader()
        val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)
        val source = IosLocalPhotoDataSource()
        val id = assertNotNull(cacheIosPickerPhoto(IosPhotoFixtures.heic.iosPhotoData()))
        try {
            val result = assertIs<SuccessResult>(load(loader, "file://" + id.removePrefix("mapmory-cache://")))
            assertEquals(32, result.image.width)
            assertEquals(24, result.image.height)
        } finally {
            source.release(id)
        }
    }

    @Test
    fun heicDecodeRespectsFitAndFillSizes() = runBlocking {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(IosHeifDecoder.Factory()) }.build()
        try {
            val fit = assertIs<SuccessResult>(load(loader, IosPhotoFixtures.rotatedHeic, Size(300, 300), Scale.FIT))
            assertTrue(fit.image.width in 168..169)
            assertEquals(300, fit.image.height)
            val fill = assertIs<SuccessResult>(load(loader, IosPhotoFixtures.rotatedHeic, Size(300, 300), Scale.FILL))
            assertEquals(300, fill.image.width)
            assertTrue(fill.image.height in 533..534)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun corruptHeicFailsAndJpegStillUsesTheDefaultDecoder() = runBlocking {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(IosHeifDecoder.Factory()) }.build()
        try {
            assertIs<ErrorResult>(load(loader, IosPhotoFixtures.heic.copyOf(12)))
            val jpeg = assertIs<SuccessResult>(load(loader, IosPhotoFixtures.jpeg))
            assertEquals(32, jpeg.image.width)
            assertEquals(24, jpeg.image.height)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun originalRequestStillRespectsMaximumBitmapSize() = runBlocking {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(IosHeifDecoder.Factory()) }.build()
        try {
            val result = assertIs<SuccessResult>(load(loader, IosPhotoFixtures.rotatedHeic, maxSize = Size(512, 512)))
            assertEquals(288, result.image.width)
            assertEquals(512, result.image.height)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun factoryInspectsHeaderWithoutConsumingItAndLeavesJpegToDefaultDecoder() {
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            for ((bytes, isHeic) in listOf(IosPhotoFixtures.heic to true, IosPhotoFixtures.jpeg to false)) {
                ImageSource(Buffer().write(bytes), FileSystem.SYSTEM).use { source ->
                    val decoder = IosHeifDecoder.Factory().create(
                        SourceFetchResult(source, "application/octet-stream", coil3.decode.DataSource.DISK),
                        Options(PlatformContext.INSTANCE), loader,
                    )
                    if (isHeic) assertNotNull(decoder) else assertNull(decoder)
                    assertEquals(bytes[0], source.source().readByte())
                }
            }
        } finally {
            loader.shutdown()
        }
    }

    private suspend fun load(
        loader: ImageLoader,
        data: Any,
        size: Size = Size.ORIGINAL,
        scale: Scale = Scale.FIT,
        maxSize: Size? = null,
    ): ImageResult = loader.execute(
        ImageRequest.Builder(PlatformContext.INSTANCE).data(data).size(size).scale(scale)
            .coroutineContext(Dispatchers.Default)
            .apply { if (maxSize != null) maxBitmapSize(maxSize) }
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(),
    )
}
