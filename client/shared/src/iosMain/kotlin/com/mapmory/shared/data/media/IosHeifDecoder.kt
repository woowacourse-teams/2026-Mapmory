@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package com.mapmory.shared.data.media

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.decode.SkiaImageDecoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import okio.Buffer
import kotlin.math.ceil

/** ImageIO handles HEIF containers that the default iOS Skia decoder cannot read. */
internal class IosHeifDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val data = source.source().readByteArray().iosPhotoData()
        val (width, height) = checkNotNull(data.photoDimensions()) { "Invalid HEIF dimensions" }
        val destination = DecodeUtils.computeDstSize(width, height, options.size, options.scale, options.maxBitmapSize)
        val multiplier = DecodeUtils.computeSizeMultiplier(
            width, height, destination.first, destination.second, options.scale, options.maxBitmapSize,
        ).coerceIn(0.0, 1.0)
        val dimension = ceil(maxOf(width, height) * multiplier).toInt().coerceAtLeast(1)
        val jpeg = checkNotNull(data.photoDisplayJpeg(dimension)) { "Unable to decode HEIF photo" }
        val jpegSource = ImageSource(Buffer().write(jpeg), options.fileSystem)
        val decoded = jpegSource.use { SkiaImageDecoder(it, options).decode() }
        return DecodeResult(decoded.image, multiplier < 1.0 || decoded.isSampled)
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            // Inspect the bytes, since cached files and presigned URLs can lack a useful MIME type.
            val header = result.source.source().peek()
            if (!header.request(12)) return null
            header.skip(4)
            if (header.readUtf8(4) != "ftyp") return null
            if (header.readUtf8(4) !in HeifBrands) return null
            return IosHeifDecoder(result.source, options)
        }
    }
}

private val HeifBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")

internal fun configureIosPhotoImageLoader() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context).components { add(IosHeifDecoder.Factory()) }.build()
    }
}
