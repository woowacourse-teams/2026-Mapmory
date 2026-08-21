@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.presentation.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mapmory.shared.domain.model.Location
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.getBytes
import platform.Foundation.NSSortDescriptor
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageManager
import platform.Photos.PHImageContentModeAspectFit
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsVersionCurrent
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPhotoLibraryActions(
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (List<SelectedPhoto>) -> Unit,
    onMessage: (String) -> Unit,
): PhotoLibraryActions {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { IosPhotoLibraryController(scope) }
    controller.onPhotosPicked = onPhotosPicked
    controller.onPhotosRecommended = onPhotosRecommended
    controller.onMessage = onMessage

    return remember(controller) {
        PhotoLibraryActions(
            pickFromGallery = controller::presentPicker,
            recommendForLocation = controller::recommend,
            prepareForAdding = controller::prepareForAdding,
        )
    }
}

private class IosPhotoLibraryController(
    private val scope: CoroutineScope,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    var onPhotosPicked: (List<SelectedPhoto>) -> Unit = {}
    var onPhotosRecommended: (List<SelectedPhoto>) -> Unit = {}
    var onMessage: (String) -> Unit = {}
    private var recommendationJob: Job? = null
    private var recommendationGeneration = 0

    fun presentPicker() {
        val presenter = topViewController() ?: run {
            onMessage("사진 선택 화면을 열지 못했어요.")
            return
        }
        val configuration = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary()).apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 0
        }
        val picker = PHPickerViewController(configuration)
        picker.delegate = this
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) return

        val loaded = MutableList<SelectedPhoto?>(results.size) { null }
        var remaining = results.size
        results.forEachIndexed { index, result ->
            loadPickerResult(result) { photo ->
                loaded[index] = photo
                remaining -= 1
                if (remaining == 0) {
                    val photos = loaded.filterNotNull()
                    if (photos.isEmpty()) {
                        onMessage("선택한 사진을 읽지 못했어요.")
                    } else {
                        onPhotosPicked(photos)
                    }
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun recommend(location: Location, parentName: String?) {
        val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
        when (status) {
            PHAuthorizationStatusAuthorized -> {
                findRecommendations(location)
            }
            PHAuthorizationStatusLimited -> onMessage(FullGalleryAccessMessage)
            PHAuthorizationStatusNotDetermined -> {
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { newStatus ->
                    onMain {
                        when (newStatus) {
                            PHAuthorizationStatusAuthorized -> findRecommendations(location)
                            PHAuthorizationStatusLimited -> onMessage(FullGalleryAccessMessage)
                            else -> onMessage("장소 기반 추천을 사용하려면 사진 접근을 허용해 주세요.")
                        }
                    }
                }
            }
            else -> onMessage("장소 기반 추천을 사용하려면 설정에서 사진 접근을 허용해 주세요.")
        }
    }

    private fun findRecommendations(location: Location) {
        recommendationJob?.cancel()
        val generation = ++recommendationGeneration
        recommendationJob = scope.launch {
            val region = withContext(Dispatchers.Default) {
                runCatching { location.photoRecommendationRegion() }.getOrNull()
            }
            if (region == null) {
                onMessage("선택한 장소의 경계를 확인하지 못했어요.")
                return@launch
            }
            val matchingAssets = withContext(Dispatchers.Default) {
                findAssetsInRegion(region)
            }
            if (generation != recommendationGeneration) return@launch
            if (matchingAssets.isEmpty()) {
                onPhotosRecommended(emptyList())
            } else {
                loadAssetPreviews(matchingAssets) { photos ->
                    if (generation == recommendationGeneration) {
                        onPhotosRecommended(photos)
                    }
                }
            }
        }
    }

    private fun findAssetsInRegion(region: PhotoRecommendationRegion): List<PHAsset> {
        val options = PHFetchOptions().apply {
            sortDescriptors = listOf(NSSortDescriptor("creationDate", ascending = false))
        }
        val result = PHAsset.fetchAssetsWithMediaType(PHAssetMediaTypeImage, options)
        return buildList {
            for (index in 0 until result.count.toInt()) {
                val asset = result.objectAtIndex(index.toULong()) as? PHAsset ?: continue
                val coordinate = asset.location?.coordinate ?: continue
                val matches = coordinate.useContents {
                    region.contains(latitude = latitude, longitude = longitude)
                }
                if (matches) add(asset)
                if (size >= MaxRecommendedPhotos) break
            }
        }
    }

    private fun loadPickerResult(result: PHPickerResult, completion: (SelectedPhoto?) -> Unit) {
        val asset = result.assetIdentifier?.let(::assetForIdentifier)
        if (asset != null) {
            loadAssetPreview(asset) { preview ->
                if (preview == null) {
                    completion(null)
                } else {
                    prepareForAdding(listOf(preview)) { prepared ->
                        completion(prepared.firstOrNull())
                    }
                }
            }
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            if (data == null || data.length == 0UL) {
                onMain { completion(null) }
                return@loadDataRepresentationForTypeIdentifier
            }
            val originalBytes = data.toByteArray()
            val previewBytes = data.toPreviewByteArray() ?: originalBytes
            onMain {
                completion(
                    SelectedPhoto(
                        id = result.assetIdentifier ?: "ios-${data.hash}",
                        displayName = result.itemProvider.suggestedName ?: "여행 사진",
                        previewBytes = previewBytes,
                        originalBytes = originalBytes,
                    ),
                )
            }
        }
    }

    private fun loadAssetPreviews(
        assets: List<PHAsset>,
        completion: (List<SelectedPhoto>) -> Unit,
    ) {
        val loaded = MutableList<SelectedPhoto?>(assets.size) { null }
        var remaining = assets.size
        assets.forEachIndexed { index, asset ->
            loadAssetPreview(asset) { photo ->
                loaded[index] = photo
                remaining -= 1
                val available = loaded.filterNotNull()
                if (available.isNotEmpty() || remaining == 0) {
                    completion(available)
                }
            }
        }
    }

    private fun loadAssetPreview(asset: PHAsset, completion: (SelectedPhoto?) -> Unit) {
        val options = PHImageRequestOptions().apply {
            version = PHImageRequestOptionsVersionCurrent
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
            networkAccessAllowed = true
        }
        var didComplete = false
        PHImageManager.defaultManager().requestImageForAsset(
            asset = asset,
            targetSize = CGSizeMake(PreviewSizePx.toDouble(), PreviewSizePx.toDouble()),
            contentMode = PHImageContentModeAspectFit,
            options = options,
        ) { image, _ ->
            val coordinate = asset.location?.coordinate
            val latitude = coordinate?.useContents { latitude }
            val longitude = coordinate?.useContents { longitude }
            val previewBytes = image
                ?.let { UIImageJPEGRepresentation(it, PreviewJpegQuality) }
                ?.toByteArray()
            onMain {
                if (didComplete) return@onMain
                didComplete = true
                completion(
                    previewBytes?.let {
                        SelectedPhoto(
                            id = asset.localIdentifier,
                            displayName = asset.displayName(),
                            previewBytes = previewBytes,
                            latitude = latitude,
                            longitude = longitude,
                            capturedAt = asset.creationDate?.formattedPhotoDate(),
                        )
                    },
                )
            }
        }
    }

    fun prepareForAdding(
        photos: List<SelectedPhoto>,
        completion: (List<SelectedPhoto>) -> Unit,
    ) {
        if (photos.isEmpty()) {
            completion(emptyList())
            return
        }

        val prepared = MutableList<SelectedPhoto?>(photos.size) { null }
        var remaining = photos.size
        fun completeOne(index: Int, photo: SelectedPhoto?) {
            prepared[index] = photo
            remaining -= 1
            if (remaining == 0) {
                val result = prepared.filterNotNull()
                completion(result)
                if (result.size != photos.size) {
                    onMessage("일부 사진의 원본을 읽지 못했어요.")
                }
            }
        }

        photos.forEachIndexed { index, photo ->
            if (photo.originalBytes != null) {
                completeOne(index, photo)
                return@forEachIndexed
            }
            val asset = assetForIdentifier(photo.id)
            if (asset == null) {
                completeOne(index, null)
                return@forEachIndexed
            }
            loadOriginalBytes(asset) { bytes ->
                completeOne(index, bytes?.let { photo.copy(originalBytes = it) })
            }
        }
    }

    private fun loadOriginalBytes(asset: PHAsset, completion: (ByteArray?) -> Unit) {
        val resources = PHAssetResource.assetResourcesForAsset(asset)
            .filterIsInstance<PHAssetResource>()
        val resource = resources.firstOrNull { it.type == PHAssetResourceTypePhoto }
            ?: resources.firstOrNull()
        if (resource == null) {
            completion(null)
            return
        }

        val chunks = mutableListOf<ByteArray>()
        val options = PHAssetResourceRequestOptions().apply {
            networkAccessAllowed = true
        }
        PHAssetResourceManager.defaultManager().requestDataForAssetResource(
            resource,
            options,
            dataReceivedHandler = { data -> data?.let { chunks += it.toByteArray() } },
            completionHandler = { error ->
                val bytes = if (error == null) chunks.joinToByteArray() else null
                onMain { completion(bytes) }
            },
        )
    }

    private fun assetForIdentifier(identifier: String): PHAsset? =
        PHAsset.fetchAssetsWithLocalIdentifiers(listOf(identifier), null).firstObject as? PHAsset
}

private fun PHAsset.displayName(): String =
    (PHAssetResource.assetResourcesForAsset(this).firstOrNull() as? PHAssetResource)
        ?.originalFilename
        ?: "여행 사진"

private fun NSDate.formattedPhotoDate(): String = NSDateFormatter().run {
    dateFormat = "yyyy.MM.dd"
    stringFromDate(this@formattedPhotoDate)
}

private fun NSData.toByteArray(): ByteArray {
    if (length == 0UL) return ByteArray(0)
    return ByteArray(length.toInt()).also { bytes ->
        bytes.usePinned { pinned -> getBytes(pinned.addressOf(0), length) }
    }
}

private fun List<ByteArray>.joinToByteArray(): ByteArray {
    val result = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(result, destinationOffset = offset)
        offset += chunk.size
    }
    return result
}

private fun NSData.toPreviewByteArray(): ByteArray? {
    val retainedData = CFBridgingRetain(this) ?: return null
    val imageSource = CGImageSourceCreateWithData(retainedData.reinterpret(), null)
    CFRelease(retainedData)
    if (imageSource == null) return null

    val thumbnailOptions = mapOf(
        kCGImageSourceCreateThumbnailFromImageAlways to true,
        kCGImageSourceCreateThumbnailWithTransform to true,
        kCGImageSourceThumbnailMaxPixelSize to PreviewSizePx,
    )
    val retainedOptions = CFBridgingRetain(thumbnailOptions) ?: run {
        CFRelease(imageSource.reinterpret())
        return null
    }
    val thumbnail = CGImageSourceCreateThumbnailAtIndex(
        imageSource,
        0UL,
        retainedOptions.reinterpret(),
    )
    CFRelease(retainedOptions)
    CFRelease(imageSource.reinterpret())
    if (thumbnail == null) return null

    val previewImage = UIImage.imageWithCGImage(thumbnail)
    CGImageRelease(thumbnail)
    val previewData = UIImageJPEGRepresentation(
        previewImage,
        PreviewJpegQuality,
    ) ?: return null
    return previewData.toByteArray()
}

private fun topViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.keyWindow
        ?: application.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

private fun onMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue(), block)
}

private const val PreviewSizePx = 2048
private const val PreviewJpegQuality = 0.96
private const val FullGalleryAccessMessage =
    "위치 기반 사진 추천을 사용하려면 전체 갤러리 접근 권한을 허용해 주세요."
