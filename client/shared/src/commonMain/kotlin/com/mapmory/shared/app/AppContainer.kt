package com.mapmory.shared.app

import com.mapmory.shared.analytics.*
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
import com.mapmory.shared.data.remote.ServiceFeedbackRemoteRepository
import com.mapmory.shared.data.remote.ShareVideoRemoteRepository
import com.mapmory.shared.data.remote.createHttpClient
import com.mapmory.shared.data.remote.installMapmoryAuthRetry
import com.mapmory.shared.data.repository.AuthenticatedMapSummaryRepository
import com.mapmory.shared.data.repository.AuthenticatedPlaceRepository
import com.mapmory.shared.data.repository.AuthenticatedTagRepository
import com.mapmory.shared.data.repository.AuthenticatedTripRecordRepository
import com.mapmory.shared.data.repository.AuthenticatedTripStatisticsRepository
import com.mapmory.shared.data.repository.AuthenticatedServiceFeedbackRepository
import com.mapmory.shared.data.repository.AuthenticatedShareVideoRepository
import com.mapmory.shared.data.repository.CachedTripRecordRepository
import com.mapmory.shared.data.repository.TripRecordListCache
import com.mapmory.shared.data.repository.MemoryTripRecordListCache
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
import com.mapmory.shared.data.settings.MemoryTeamDevicePreference
import com.mapmory.shared.data.settings.MemoryThemePreference
import com.mapmory.shared.data.settings.MemoryShareVideoJobStore
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.data.settings.OnboardingPreference
import com.mapmory.shared.data.settings.TeamDevicePreference
import com.mapmory.shared.data.settings.ThemePreference
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.PlaceRepository
import com.mapmory.shared.domain.repository.TagRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.TripStatisticsRepository
import com.mapmory.shared.domain.repository.ServiceFeedbackRepository
import com.mapmory.shared.domain.repository.ShareVideoRepository
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
    val accountLoginController: com.mapmory.shared.presentation.auth.AccountLoginController? get() = null
    val accountSession: com.mapmory.shared.data.auth.AccountSession? get() = null
    val groupRepository: com.mapmory.shared.data.remote.GroupRepository? get() = null

    val regionCatalog: RegionCatalog
    val tripRecordRepository: TripRecordRepository
    val mapSummaryRepository: MapSummaryRepository
    val tripStatisticsRepository: TripStatisticsRepository
    val serviceFeedbackRepository: ServiceFeedbackRepository?
    val shareVideoRepository: ShareVideoRepository?
    val shareVideoJobStore: ShareVideoJobStore
    val tagRepository: TagRepository
    val themePreference: ThemePreference
    val onboardingPreference: OnboardingPreference
    val teamDevicePreference: TeamDevicePreference
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
    override val serviceFeedbackRepository: ServiceFeedbackRepository?,
    override val shareVideoRepository: ShareVideoRepository?,
    override val shareVideoJobStore: ShareVideoJobStore,
    override val tagRepository: TagRepository,
    private val placeRepository: PlaceRepository?,
    override val themePreference: ThemePreference,
    override val onboardingPreference: OnboardingPreference,
    override val teamDevicePreference: TeamDevicePreference,
    private val thumbnailLoader: TripRecordThumbnailLoader?,
    private val photoPreviewCache: PhotoPreviewCache?,
    backgroundSaveExecution: BackgroundSaveExecution,
    private val onClose: () -> Unit,
    localPhotoDataSource: LocalPhotoDataSource?,
    analytics: MapmoryAnalytics,
    override val accountSession: com.mapmory.shared.data.auth.AccountSession?,
    override val groupRepository: com.mapmory.shared.data.remote.GroupRepository?,
    private val invalidateSummaryCaches: () -> Unit,
) : AppContainer {
    override val accountLoginController = accountSession?.let {
        com.mapmory.shared.presentation.auth.AccountLoginController(it, analytics)
    }
    private val recordedPhotoIndex = com.mapmory.shared.data.media.RecordedPhotoIndex(photoPreviewCache)
    override val tripRecordRepository: TripRecordRepository = run {
        val indexed = com.mapmory.shared.data.repository.PhotoUsageTripRecordRepository(tripRecordRepository, recordedPhotoIndex)
        (accountSession as? GuestSessionManager)?.let { AuthenticatedTripRecordRepository(it, indexed) } ?: indexed
    }
    private val backgroundSaveScope = CoroutineScope(SupervisorJob() + backgroundSaveDispatcher())
    private val mutableTripRecordRevision = MutableStateFlow(0L)
    override val tripRecordRevision: StateFlow<Long> = mutableTripRecordRevision.asStateFlow()
    private val notifyTripRecordsChanged = {
        invalidateSummaryCaches()
        mutableTripRecordRevision.update { revision -> revision + 1 }
    }
    override val backgroundTripRecordSaver = BackgroundTripRecordSaver(
        repository = this.tripRecordRepository,
        scope = backgroundSaveScope,
        execution = backgroundSaveExecution,
        photoPreviewCache = photoPreviewCache,
        onSaved = notifyTripRecordsChanged,
        localPhotoDataSource = localPhotoDataSource,
        analytics = analytics,
        canEnqueue = { accountSession?.state?.value?.let { !it.transitioning && !it.pendingLogin && !it.pendingLogout } != false },
    )

    init {
        (accountSession as? GuestSessionManager)?.apply {
            val clearOtherCaches = onAccountChanged
            onAccountChanged = {
                clearOtherCaches()
                recordedPhotoIndex.clearForAccountChange()
            }
            canChangeAccount = { backgroundTripRecordSaver.saves.value.isEmpty() &&
                shareVideoJobStore.state.value.isCreationRequestInProgress.not() }
        }
    }

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
        accountLoginController?.close()
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
    serviceFeedbackRepository: ServiceFeedbackRepository? = null,
    tripStatisticsCache: TripStatisticsCache = MemoryTripStatisticsCache(),
    tagRepository: TagRepository = requireNotNull(
        tripRecordRepository as? TagRepository,
    ) { "태그 Repository를 함께 전달해 주세요." },
    regionCatalog: RegionCatalog = StaticRegionCatalog(),
    themePreference: ThemePreference = MemoryThemePreference(),
    onboardingPreference: OnboardingPreference = MemoryOnboardingPreference(),
    teamDevicePreference: TeamDevicePreference = MemoryTeamDevicePreference(),
    thumbnailLoader: TripRecordThumbnailLoader? = null,
    photoPreviewCache: PhotoPreviewCache? = null,
    backgroundSaveExecution: BackgroundSaveExecution = DirectBackgroundSaveExecution,
    placeRepository: PlaceRepository? = null,
    onClose: () -> Unit = {},
    localPhotoDataSource: LocalPhotoDataSource? = null,
    analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
    shareVideoRepository: ShareVideoRepository? = null,
    shareVideoJobStore: ShareVideoJobStore = MemoryShareVideoJobStore(),
    accountSession: com.mapmory.shared.data.auth.AccountSession? = null,
    groupRepository: com.mapmory.shared.data.remote.GroupRepository? = null,
): AppContainer {
    val cachedTripStatistics = CachedTripStatisticsRepository(
        delegate = AnalyticsStatisticsRepository(tripStatisticsRepository, analytics),
        cache = tripStatisticsCache,
    )
    val cachedMapSummary = CachedMapSummaryRepository(
        delegate = AnalyticsMapSummaryRepository(mapSummaryRepository, analytics),
        cache = mapSummaryCache,
    )
    val session = accountSession as? GuestSessionManager
    session?.let {
        val clearRemoteCaches = it.onAccountChanged
        it.onAccountChanged = {
            clearRemoteCaches()
            cachedMapSummary.invalidate()
            cachedTripStatistics.invalidate()
        }
    }
    return DefaultAppContainer(
        regionCatalog = regionCatalog,
        tripRecordRepository = AnalyticsTripRecordRepository(tripRecordRepository, analytics),
        mapSummaryRepository = session?.let { AuthenticatedMapSummaryRepository(it, cachedMapSummary) } ?: cachedMapSummary,
        tripStatisticsRepository = session?.let { AuthenticatedTripStatisticsRepository(it, cachedTripStatistics) } ?: cachedTripStatistics,
        serviceFeedbackRepository = serviceFeedbackRepository?.let { AnalyticsFeedbackRepository(it, analytics) },
        shareVideoRepository = shareVideoRepository?.let { com.mapmory.shared.analytics.AnalyticsShareVideoRepository(it, analytics) },
        shareVideoJobStore = shareVideoJobStore,
        tagRepository = AnalyticsTagRepository(tagRepository, analytics),
        placeRepository = placeRepository?.let { AnalyticsPlaceRepository(it, analytics) },
        themePreference = themePreference,
        onboardingPreference = onboardingPreference,
        teamDevicePreference = teamDevicePreference,
        thumbnailLoader = thumbnailLoader,
        photoPreviewCache = photoPreviewCache,
        backgroundSaveExecution = backgroundSaveExecution,
        localPhotoDataSource = localPhotoDataSource,
        onClose = onClose,
        analytics = analytics,
        accountSession = accountSession,
        groupRepository = groupRepository,
        invalidateSummaryCaches = { cachedMapSummary.invalidate(); cachedTripStatistics.invalidate() },
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
        serviceFeedbackRepository = ServiceFeedbackRemoteRepository(
            client = client,
            apiBaseUrl = apiBaseUrl,
            accessTokenProvider = accessTokenProvider,
        ),
        shareVideoRepository = ShareVideoRemoteRepository(
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
    tripRecordListCache: TripRecordListCache = MemoryTripRecordListCache(),
    themePreference: ThemePreference = MemoryThemePreference(),
    onboardingPreference: OnboardingPreference = MemoryOnboardingPreference(),
    teamDevicePreference: TeamDevicePreference = MemoryTeamDevicePreference(),
    localPhotoDataSource: LocalPhotoDataSource? = null,
    backgroundSaveExecution: BackgroundSaveExecution = DirectBackgroundSaveExecution,
    onAuthRefreshFailed: (stage: String, error: Throwable) -> Unit = { _, _ -> },
    analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
    shareVideoJobStore: ShareVideoJobStore = MemoryShareVideoJobStore(),
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
        tripRecordListCache = tripRecordListCache,
        themePreference = themePreference,
        onboardingPreference = onboardingPreference,
        teamDevicePreference = teamDevicePreference,
        shareVideoJobStore = shareVideoJobStore,
        localPhotoDataSource = localPhotoDataSource,
        backgroundSaveExecution = backgroundSaveExecution,
        onAuthRefreshFailed = onAuthRefreshFailed,
        onClose = client::close,
        analytics = analytics,
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
    tripRecordListCache: TripRecordListCache = MemoryTripRecordListCache(),
    themePreference: ThemePreference = MemoryThemePreference(),
    onboardingPreference: OnboardingPreference = MemoryOnboardingPreference(),
    teamDevicePreference: TeamDevicePreference = MemoryTeamDevicePreference(),
    localPhotoDataSource: LocalPhotoDataSource? = null,
    backgroundSaveExecution: BackgroundSaveExecution = DirectBackgroundSaveExecution,
    onAuthRefreshFailed: (stage: String, error: Throwable) -> Unit = { _, _ -> },
    onClose: () -> Unit = client::close,
    analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
    shareVideoJobStore: ShareVideoJobStore = MemoryShareVideoJobStore(),
): AppContainer {
    val discardRecordCache = runCatching { tokenStore.read().tokens == null || tokenStore.pendingProvider().provider != null }.getOrDefault(false)
    if (discardRecordCache) {
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
    val remoteServiceFeedback = ServiceFeedbackRemoteRepository(
        client = client,
        apiBaseUrl = apiBaseUrl,
        accessTokenProvider = session,
    )
    val remoteShareVideos = ShareVideoRemoteRepository(
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
        localPhotoDataSource = localPhotoDataSource,
    )

    val cachedTripRecords = CachedTripRecordRepository(cachedMediaTripRecords, tripRecordListCache, discardRecordCache)
    session.onAccountChanged = {
        cachedTripRecords.clearForAccountChange()
        mapSummaryCache.clear()
        tripStatisticsCache.clear()
        shareVideoJobStore.clear()
    }
    return createAppContainer(
        accountSession = session,
        groupRepository = com.mapmory.shared.data.remote.GroupRemoteRepository(client, apiBaseUrl, session),
        tripRecordRepository = AuthenticatedTripRecordRepository(session, cachedTripRecords),
        mapSummaryRepository = remoteMapSummary,
        mapSummaryCache = mapSummaryCache,
        tripStatisticsRepository = remoteTripStatistics,
        serviceFeedbackRepository = AuthenticatedServiceFeedbackRepository(session, remoteServiceFeedback),
        shareVideoRepository = AuthenticatedShareVideoRepository(session, remoteShareVideos),
        shareVideoJobStore = shareVideoJobStore,
        tripStatisticsCache = tripStatisticsCache,
        tagRepository = AuthenticatedTagRepository(session, remoteTags),
        placeRepository = AuthenticatedPlaceRepository(session, remotePlaces),
        regionCatalog = regionCatalog,
        themePreference = themePreference,
        onboardingPreference = onboardingPreference,
        teamDevicePreference = teamDevicePreference,
        thumbnailLoader = CachedTripRecordThumbnailLoader(photoPreviewLoader),
        photoPreviewCache = photoPreviewCache,
        backgroundSaveExecution = backgroundSaveExecution,
        localPhotoDataSource = localPhotoDataSource,
        onClose = { session.close(); onClose() },
        analytics = analytics,
    )
}
