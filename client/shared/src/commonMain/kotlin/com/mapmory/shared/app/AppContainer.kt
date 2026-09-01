package com.mapmory.shared.app

import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.data.media.CachedTripRecordThumbnailLoader
import com.mapmory.shared.data.media.MemoryPhotoPreviewCache
import com.mapmory.shared.data.media.PhotoPreviewCache
import com.mapmory.shared.data.media.PhotoPreviewLoader
import com.mapmory.shared.data.remote.AccessTokenProvider
import com.mapmory.shared.data.remote.AuthRemoteRepository
import com.mapmory.shared.data.remote.MapSummaryRemoteRepository
import com.mapmory.shared.data.remote.PhotoUploadRemoteRepository
import com.mapmory.shared.data.remote.PresignedPhotoRemoteSource
import com.mapmory.shared.data.remote.TripRecordRemoteRepository
import com.mapmory.shared.data.remote.createHttpClient
import com.mapmory.shared.data.remote.installMapmoryAuthRetry
import com.mapmory.shared.data.repository.AuthenticatedMapSummaryRepository
import com.mapmory.shared.data.repository.AuthenticatedTripRecordRepository
import com.mapmory.shared.data.repository.CachedMediaTripRecordRepository
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.data.repository.UploadingTripRecordRepository
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.usecase.CreateTripRecordUseCase
import com.mapmory.shared.domain.usecase.DeleteTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordsUseCase
import com.mapmory.shared.domain.usecase.UpdateTripRecordUseCase
import com.mapmory.shared.presentation.map.viewmodel.MapViewModel
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordDetailViewModel
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordEditorViewModel
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordListViewModel
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoader
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val MAPMORY_API_BASE_URL = "https://api.map-mory.com/api/v1"

interface AppContainer {
    val regionCatalog: RegionCatalog
    val tripRecordRepository: TripRecordRepository
    val mapSummaryRepository: MapSummaryRepository
    val viewModelFactory: MapmoryViewModelFactory
    val tripRecordRevision: StateFlow<Long>

    fun close() = Unit
}

interface MapmoryViewModelFactory {
    fun createMapViewModel(): MapViewModel

    fun createTripRecordListViewModel(): TripRecordListViewModel

    fun createTripRecordDetailViewModel(): TripRecordDetailViewModel

    fun createTripRecordEditorViewModel(): TripRecordEditorViewModel
}

private class DefaultMapmoryViewModelFactory(
    private val repository: TripRecordRepository,
    private val mapSummaryRepository: MapSummaryRepository,
    private val regionCatalog: RegionCatalog,
    private val thumbnailLoader: TripRecordThumbnailLoader?,
    private val onTripRecordsChanged: () -> Unit,
) : MapmoryViewModelFactory {
    override fun createMapViewModel(): MapViewModel = MapViewModel(
        mapSummaryRepository = mapSummaryRepository,
        regionCatalog = regionCatalog,
    )

    override fun createTripRecordListViewModel(): TripRecordListViewModel =
        TripRecordListViewModel(
            getTripRecords = GetTripRecordsUseCase(repository),
            regionCatalog = regionCatalog,
            thumbnailLoader = thumbnailLoader,
        )

    override fun createTripRecordDetailViewModel(): TripRecordDetailViewModel =
        TripRecordDetailViewModel(
            getTripRecord = GetTripRecordUseCase(repository),
            deleteTripRecord = DeleteTripRecordUseCase(repository),
            regionCatalog = regionCatalog,
            onTripRecordsChanged = onTripRecordsChanged,
        )

    override fun createTripRecordEditorViewModel(): TripRecordEditorViewModel =
        TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
            getTripRecord = GetTripRecordUseCase(repository),
            regionCatalog = regionCatalog,
            onTripRecordsChanged = onTripRecordsChanged,
        )
}

private class DefaultAppContainer(
    override val regionCatalog: RegionCatalog,
    override val tripRecordRepository: TripRecordRepository,
    override val mapSummaryRepository: MapSummaryRepository,
    private val thumbnailLoader: TripRecordThumbnailLoader?,
    private val onClose: () -> Unit,
) : AppContainer {
    private val mutableTripRecordRevision = MutableStateFlow(0L)
    override val tripRecordRevision: StateFlow<Long> = mutableTripRecordRevision.asStateFlow()

    override val viewModelFactory: MapmoryViewModelFactory = DefaultMapmoryViewModelFactory(
        repository = tripRecordRepository,
        mapSummaryRepository = mapSummaryRepository,
        regionCatalog = regionCatalog,
        thumbnailLoader = thumbnailLoader,
        onTripRecordsChanged = {
            mutableTripRecordRevision.update { revision -> revision + 1 }
        },
    )

    override fun close() = onClose()
}

fun createAppContainer(
    tripRecordRepository: TripRecordRepository,
    mapSummaryRepository: MapSummaryRepository = requireNotNull(
        tripRecordRepository as? MapSummaryRepository,
    ) { "지도 요약 Repository를 함께 전달해 주세요." },
    regionCatalog: RegionCatalog = StaticRegionCatalog(),
    thumbnailLoader: TripRecordThumbnailLoader? = null,
    onClose: () -> Unit = {},
): AppContainer = DefaultAppContainer(
    regionCatalog = regionCatalog,
    tripRecordRepository = tripRecordRepository,
    mapSummaryRepository = mapSummaryRepository,
    thumbnailLoader = thumbnailLoader,
    onClose = onClose,
)

fun createInMemoryAppContainer(
    now: () -> String = { "2026-08-24T00:00:00" },
): AppContainer {
    val regionCatalog = StaticRegionCatalog()
    return createAppContainer(
        tripRecordRepository = FakeTripRecordRepository(
            regionCatalog = regionCatalog,
            now = now,
        ),
        regionCatalog = regionCatalog,
    )
}

/** 게스트 로그인이 완료돼 토큰을 제공할 수 있다는 가정 아래 원격 구현을 조립한다. */
fun createRemoteAppContainer(
    apiBaseUrl: String,
    accessTokenProvider: AccessTokenProvider,
    regionCatalog: RegionCatalog = StaticRegionCatalog(),
): AppContainer {
    val client = createHttpClient()
    return createAppContainer(
        tripRecordRepository = TripRecordRemoteRepository(
            client = client,
            apiBaseUrl = apiBaseUrl,
            accessTokenProvider = accessTokenProvider,
            regionCatalog = regionCatalog,
        ),
        mapSummaryRepository = MapSummaryRemoteRepository(
            client = client,
            apiBaseUrl = apiBaseUrl,
            accessTokenProvider = accessTokenProvider,
        ),
        regionCatalog = regionCatalog,
        onClose = client::close,
    )
}

/** 토큰이 없으면 게스트로 로그인하고, 저장된 세션이 있으면 갱신하는 운영용 컨테이너다. */
fun createGuestRemoteAppContainer(
    tokenStore: AuthTokenStore,
    apiBaseUrl: String = MAPMORY_API_BASE_URL,
    regionCatalog: RegionCatalog = StaticRegionCatalog(),
    photoPreviewCache: PhotoPreviewCache = MemoryPhotoPreviewCache(),
): AppContainer {
    val client = createHttpClient()
    return createGuestRemoteAppContainer(
        client = client,
        apiBaseUrl = apiBaseUrl,
        tokenStore = tokenStore,
        regionCatalog = regionCatalog,
        photoPreviewCache = photoPreviewCache,
        onClose = client::close,
    )
}

internal fun createGuestRemoteAppContainer(
    client: HttpClient,
    apiBaseUrl: String,
    tokenStore: AuthTokenStore,
    regionCatalog: RegionCatalog = StaticRegionCatalog(),
    photoPreviewCache: PhotoPreviewCache = MemoryPhotoPreviewCache(),
    onClose: () -> Unit = client::close,
): AppContainer {
    val session = GuestSessionManager(
        gateway = AuthRemoteRepository(client, apiBaseUrl),
        tokenStore = tokenStore,
    )
    client.installMapmoryAuthRetry(
        session = session,
        apiBaseUrl = apiBaseUrl,
    )
    val remoteTripRecords = TripRecordRemoteRepository(
        client = client,
        apiBaseUrl = apiBaseUrl,
        accessTokenProvider = session,
        regionCatalog = regionCatalog,
    )
    val remoteMapSummary = MapSummaryRemoteRepository(
        client = client,
        apiBaseUrl = apiBaseUrl,
        accessTokenProvider = session,
    )
    val uploadingTripRecords = UploadingTripRecordRepository(
        uploader = PhotoUploadRemoteRepository(
            client = client,
            apiBaseUrl = apiBaseUrl,
            accessTokenProvider = session,
        ),
        delegate = remoteTripRecords,
    )
    val photoPreviewLoader = PhotoPreviewLoader(
        cache = photoPreviewCache,
        remoteSource = PresignedPhotoRemoteSource(client),
    )
    val cachedMediaTripRecords = CachedMediaTripRecordRepository(
        delegate = uploadingTripRecords,
        loader = photoPreviewLoader,
    )

    return createAppContainer(
        tripRecordRepository = AuthenticatedTripRecordRepository(session, cachedMediaTripRecords),
        mapSummaryRepository = AuthenticatedMapSummaryRepository(session, remoteMapSummary),
        regionCatalog = regionCatalog,
        thumbnailLoader = CachedTripRecordThumbnailLoader(photoPreviewLoader),
        onClose = onClose,
    )
}
