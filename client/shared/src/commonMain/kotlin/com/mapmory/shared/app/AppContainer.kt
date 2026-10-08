package com.mapmory.shared.app

import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.data.media.CachedTripRecordThumbnailLoader
import com.mapmory.shared.data.media.LocalPhotoDataSource
import com.mapmory.shared.data.media.MemoryPhotoPreviewCache
import com.mapmory.shared.data.media.PhotoPreviewCache
import com.mapmory.shared.data.media.PhotoPreviewLoader
import com.mapmory.shared.data.remote.AccessTokenProvider
import com.mapmory.shared.data.remote.AuthRemoteRepository
import com.mapmory.shared.data.remote.MapSummaryRemoteRepository
import com.mapmory.shared.data.remote.PhotoUploadRemoteRepository
import com.mapmory.shared.data.remote.PlaceRemoteRepository
import com.mapmory.shared.data.remote.PresignedPhotoRemoteSource
import com.mapmory.shared.data.remote.TagRemoteRepository
import com.mapmory.shared.data.remote.TripRecordRemoteRepository
import com.mapmory.shared.data.remote.TripStatisticsRemoteRepository
import com.mapmory.shared.data.remote.createHttpClient
import com.mapmory.shared.data.remote.installMapmoryAuthRetry
import com.mapmory.shared.data.repository.AuthenticatedMapSummaryRepository
import com.mapmory.shared.data.repository.AuthenticatedPlaceRepository
import com.mapmory.shared.data.repository.AuthenticatedTagRepository
import com.mapmory.shared.data.repository.AuthenticatedTripRecordRepository
import com.mapmory.shared.data.repository.AuthenticatedTripStatisticsRepository
import com.mapmory.shared.data.repository.CachedMediaTripRecordRepository
import com.mapmory.shared.data.repository.CachedMapSummaryRepository
import com.mapmory.shared.data.repository.CachedTripStatisticsRepository
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.data.repository.MemoryTripStatisticsCache
import com.mapmory.shared.data.repository.MapSummaryCache
import com.mapmory.shared.data.repository.MemoryMapSummaryCache
import com.mapmory.shared.data.repository.TripStatisticsCache
import com.mapmory.shared.data.repository.UploadingTripRecordRepository
import com.mapmory.shared.data.settings.MemoryOnboardingPreference
import com.mapmory.shared.data.settings.MemoryThemePreference
import com.mapmory.shared.data.settings.OnboardingPreference
import com.mapmory.shared.data.settings.ThemePreference
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.PlaceRepository
import com.mapmory.shared.domain.repository.TagRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.TripStatisticsRepository
import com.mapmory.shared.domain.usecase.CreateTripRecordUseCase
import com.mapmory.shared.domain.usecase.CreateTagUseCase
import com.mapmory.shared.domain.usecase.DeleteTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordsUseCase
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.domain.usecase.UpdateTripRecordUseCase
import com.mapmory.shared.presentation.map.viewmodel.MapViewModel
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordDetailViewModel
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordEditorViewModel
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordListViewModel
import com.mapmory.shared.presentation.triprecord.viewmodel.TripStatisticsViewModel
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoader
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val MAPMORY_API_BASE_URL = "https://api.map-mory.com/api/v1"

interface AppContainer {
    val regionCatalog: RegionCatalog
    val tripRecordRepository: TripRecordRepository
    val mapSummaryRepository: MapSummaryRepository
    val tripStatisticsRepository: TripStatisticsRepository
    val tagRepository: TagRepository
    val themePreference: ThemePreference
    val onboardingPreference: OnboardingPreference
    val viewModelFactory: MapmoryViewModelFactory
    val tripRecordRevision: StateFlow<Long>
    val backgroundTripRecordSaver: BackgroundTripRecordSaver

    fun close() = Unit
}

interface MapmoryViewModelFactory {
    fun createMapViewModel(): MapViewModel

    fun createTripRecordListViewModel(): TripRecordListViewModel

    fun createTripStatisticsViewModel(): TripStatisticsViewModel

    fun createTripRecordDetailViewModel(): TripRecordDetailViewModel

    fun createTripRecordEditorViewModel(): TripRecordEditorViewModel
}

private class DefaultMapmoryViewModelFactory(
    private val recordedPhotoIndex: com.mapmory.shared.data.media.RecordedPhotoIndex,
    private val repository: TripRecordRepository,
    private val mapSummaryRepository: MapSummaryRepository,
    private val tripStatisticsRepository: TripStatisticsRepository,
    private val tagRepository: TagRepository,
    private val placeRepository: PlaceRepository?,
    private val regionCatalog: RegionCatalog,
    private val thumbnailLoader: TripRecordThumbnailLoader?,
    private val onTripRecordsChanged: () -> Unit,
    private val backgroundTripRecordSaver: BackgroundTripRecordSaver,
) : MapmoryViewModelFactory {
    override fun createMapViewModel(): MapViewModel = MapViewModel(
        mapSummaryRepository = mapSummaryRepository,
        regionCatalog = regionCatalog,
        getTags = GetTagsUseCase(tagRepository),
    )

    override fun createTripRecordListViewModel(): TripRecordListViewModel =
        TripRecordListViewModel(
            getTripRecords = GetTripRecordsUseCase(repository),
            regionCatalog = regionCatalog,
            thumbnailLoader = thumbnailLoader,
            getTags = GetTagsUseCase(tagRepository),
        )

    override fun createTripStatisticsViewModel(): TripStatisticsViewModel =
        TripStatisticsViewModel(tripStatisticsRepository)

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
            getTags = GetTagsUseCase(tagRepository),
            createTag = CreateTagUseCase(tagRepository),
            backgroundTripRecordSaver = backgroundTripRecordSaver,
            recordedPhotoIndex = recordedPhotoIndex,
            placeRepository = placeRepository,
        )
}

private class DefaultAppContainer(
    override val regionCatalog: RegionCatalog,
    tripRecordRepository: TripRecordRepository,
    override val mapSummaryRepository: MapSummaryRepository,
    override val tripStatisticsRepository: TripStatisticsRepository,
    override val tagRepository: TagRepository,
    private val placeRepository: PlaceRepository?,
    override val themePreference: ThemePreference,
    override val onboardingPreference: OnboardingPreference,
    private val thumbnailLoader: TripRecordThumbnailLoader?,
    private val photoPreviewCache: PhotoPreviewCache?,
    backgroundSaveExecution: BackgroundSaveExecution,
    private val onClose: () -> Unit,
) : AppContainer {
    private val recordedPhotoIndex = com.mapmory.shared.data.media.RecordedPhotoIndex(photoPreviewCache)
    override val tripRecordRepository: TripRecordRepository =
        com.mapmory.shared.data.repository.PhotoUsageTripRecordRepository(tripRecordRepository, recordedPhotoIndex)
    private val backgroundSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableTripRecordRevision = MutableStateFlow(0L)
    override val tripRecordRevision: StateFlow<Long> = mutableTripRecordRevision.asStateFlow()
    private val notifyTripRecordsChanged = {
        (mapSummaryRepository as? CachedMapSummaryRepository)?.invalidate()
        (tripStatisticsRepository as? CachedTripStatisticsRepository)?.invalidate()
        mutableTripRecordRevision.update { revision -> revision + 1 }
    }
    override val backgroundTripRecordSaver = BackgroundTripRecordSaver(
        repository = this.tripRecordRepository,
        scope = backgroundSaveScope,
        execution = backgroundSaveExecution,
        photoPreviewCache = photoPreviewCache,
        onSaved = notifyTripRecordsChanged,
    )

    override val viewModelFactory: MapmoryViewModelFactory = DefaultMapmoryViewModelFactory(
        recordedPhotoIndex = recordedPhotoIndex,
        repository = this.tripRecordRepository,
        mapSummaryRepository = mapSummaryRepository,
        tripStatisticsRepository = tripStatisticsRepository,
        tagRepository = tagRepository,
        placeRepository = placeRepository,
        regionCatalog = regionCatalog,
        thumbnailLoader = thumbnailLoader,
        onTripRecordsChanged = notifyTripRecordsChanged,
        backgroundTripRecordSaver = backgroundTripRecordSaver,
    )

    override fun close() {
        backgroundSaveScope.cancel()
        onClose()
    }
}

fun createAppContainer(
    tripRecordRepository: TripRecordRepository,
    mapSummaryRepository: MapSummaryRepository = requireNotNull(
        tripRecordRepository as? MapSummaryRepository,
    ) { "지도 요약 Repository를 함께 전달해 주세요." },
    mapSummaryCache: MapSummaryCache = MemoryMapSummaryCache(),
    tripStatisticsRepository: TripStatisticsRepository = requireNotNull(
        tripRecordRepository as? TripStatisticsRepository,
    ) { "여행 통계 Repository를 함께 전달해 주세요." },
    tripStatisticsCache: TripStatisticsCache = MemoryTripStatisticsCache(),
    tagRepository: TagRepository = requireNotNull(
        tripRecordRepository as? TagRepository,
    ) { "태그 Repository를 함께 전달해 주세요." },
    regionCatalog: RegionCatalog = StaticRegionCatalog(),
    themePreference: ThemePreference = MemoryThemePreference(),
    onboardingPreference: OnboardingPreference = MemoryOnboardingPreference(),
    thumbnailLoader: TripRecordThumbnailLoader? = null,
    photoPreviewCache: PhotoPreviewCache? = null,
    backgroundSaveExecution: BackgroundSaveExecution = DirectBackgroundSaveExecution,
    placeRepository: PlaceRepository? = null,
    onClose: () -> Unit = {},
): AppContainer {
    val cachedTripStatistics = CachedTripStatisticsRepository(
        delegate = tripStatisticsRepository,
        cache = tripStatisticsCache,
    )
    val cachedMapSummary = CachedMapSummaryRepository(
        delegate = mapSummaryRepository,
        cache = mapSummaryCache,
    )
    return DefaultAppContainer(
        regionCatalog = regionCatalog,
        tripRecordRepository = tripRecordRepository,
        mapSummaryRepository = cachedMapSummary,
        tripStatisticsRepository = cachedTripStatistics,
        tagRepository = tagRepository,
        placeRepository = placeRepository,
        themePreference = themePreference,
        onboardingPreference = onboardingPreference,
        thumbnailLoader = thumbnailLoader,
        photoPreviewCache = photoPreviewCache,
        backgroundSaveExecution = backgroundSaveExecution,
        onClose = onClose,
    )
}

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
        tripStatisticsRepository = TripStatisticsRemoteRepository(
            client = client,
            apiBaseUrl = apiBaseUrl,
            accessTokenProvider = accessTokenProvider,
        ),
        tagRepository = TagRemoteRepository(
            client = client,
            apiBaseUrl = apiBaseUrl,
            accessTokenProvider = accessTokenProvider,
        ),
        placeRepository = PlaceRemoteRepository(
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
    mapSummaryCache: MapSummaryCache = MemoryMapSummaryCache(),
    tripStatisticsCache: TripStatisticsCache = MemoryTripStatisticsCache(),
    themePreference: ThemePreference = MemoryThemePreference(),
    onboardingPreference: OnboardingPreference = MemoryOnboardingPreference(),
    localPhotoDataSource: LocalPhotoDataSource? = null,
    backgroundSaveExecution: BackgroundSaveExecution = DirectBackgroundSaveExecution,
    onAuthRefreshFailed: (stage: String, error: Throwable) -> Unit = { _, _ -> },
): AppContainer {
    val client = createHttpClient()
    return createGuestRemoteAppContainer(
        client = client,
        apiBaseUrl = apiBaseUrl,
        tokenStore = tokenStore,
        regionCatalog = regionCatalog,
        photoPreviewCache = photoPreviewCache,
        mapSummaryCache = mapSummaryCache,
        tripStatisticsCache = tripStatisticsCache,
        themePreference = themePreference,
        onboardingPreference = onboardingPreference,
        localPhotoDataSource = localPhotoDataSource,
        backgroundSaveExecution = backgroundSaveExecution,
        onAuthRefreshFailed = onAuthRefreshFailed,
        onClose = client::close,
    )
}

internal fun createGuestRemoteAppContainer(
    client: HttpClient,
    apiBaseUrl: String,
    tokenStore: AuthTokenStore,
    regionCatalog: RegionCatalog = StaticRegionCatalog(),
    photoPreviewCache: PhotoPreviewCache = MemoryPhotoPreviewCache(),
    mapSummaryCache: MapSummaryCache = MemoryMapSummaryCache(),
    tripStatisticsCache: TripStatisticsCache = MemoryTripStatisticsCache(),
    themePreference: ThemePreference = MemoryThemePreference(),
    onboardingPreference: OnboardingPreference = MemoryOnboardingPreference(),
    localPhotoDataSource: LocalPhotoDataSource? = null,
    backgroundSaveExecution: BackgroundSaveExecution = DirectBackgroundSaveExecution,
    onAuthRefreshFailed: (stage: String, error: Throwable) -> Unit = { _, _ -> },
    onClose: () -> Unit = client::close,
): AppContainer {
    if (tokenStore.load() == null) {
        mapSummaryCache.clear()
        tripStatisticsCache.clear()
    }
    val session = GuestSessionManager(
        gateway = AuthRemoteRepository(client, apiBaseUrl),
        tokenStore = tokenStore,
        onRefreshFailed = onAuthRefreshFailed,
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
    val remoteTripStatistics = TripStatisticsRemoteRepository(
        client = client,
        apiBaseUrl = apiBaseUrl,
        accessTokenProvider = session,
    )
    val remoteTags = TagRemoteRepository(
        client = client,
        apiBaseUrl = apiBaseUrl,
        accessTokenProvider = session,
    )
    val remotePlaces = PlaceRemoteRepository(
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
        localPhotoDataSource = localPhotoDataSource,
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
        mapSummaryCache = mapSummaryCache,
        tripStatisticsRepository = AuthenticatedTripStatisticsRepository(session, remoteTripStatistics),
        tripStatisticsCache = tripStatisticsCache,
        tagRepository = AuthenticatedTagRepository(session, remoteTags),
        placeRepository = AuthenticatedPlaceRepository(session, remotePlaces),
        regionCatalog = regionCatalog,
        themePreference = themePreference,
        onboardingPreference = onboardingPreference,
        thumbnailLoader = CachedTripRecordThumbnailLoader(photoPreviewLoader),
        photoPreviewCache = photoPreviewCache,
        backgroundSaveExecution = backgroundSaveExecution,
        onClose = onClose,
    )
}
