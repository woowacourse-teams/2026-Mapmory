package com.mapmory.backend.place.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.place.infrastructure.geoapify.GeoapifyClient;
import com.mapmory.backend.place.infrastructure.google.GooglePlacesClient;
import org.junit.jupiter.api.Test;

class PlaceLookupRouterTest {

    private static final String GEOAPIFY_ID = "51d1f2a3b4c5e6f7a8b9c0d1e2f3a4b5c6d7e8f9";
    private static final String GOOGLE_ID = "ChIJzRQ2iZWifDURHaD9V5e3VyM";

    private final GooglePlacesClient google = mock(GooglePlacesClient.class);
    private final GeoapifyClient geoapify = mock(GeoapifyClient.class);
    private final PlaceLookupRouter router = new PlaceLookupRouter(google, geoapify);

    @Test
    void Google_키가_있으면_Google_자동완성으로_검색한다() {
        when(google.isConfigured()).thenReturn(true);

        router.search("경복", "session-1");

        verify(google).search("경복", "session-1");
        verifyNoInteractions(geoapify);
    }

    @Test
    void Google_키가_없으면_Geoapify로_검색한다() {
        router.search("경복궁", "session-1");

        verify(geoapify).search("경복궁");
    }

    @Test
    void 세션_토큰을_보내지_않는_앱은_Google_키가_있어도_Geoapify로_검색한다() {
        when(google.isConfigured()).thenReturn(true);

        router.search("경복궁", null);

        verify(geoapify).search("경복궁");
        verify(google, never()).search(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void Google_키를_뺀_뒤에도_Google_장소는_Google로_보내_제공자_장애로_알린다() {
        router.findById(GOOGLE_ID);

        verify(google).findById(GOOGLE_ID);
        verifyNoInteractions(geoapify);
    }

    @Test
    void 기존_Geoapify_장소는_Google_키가_있어도_Geoapify로_조회한다() {
        when(google.isConfigured()).thenReturn(true);
        when(geoapify.providerCode(GEOAPIFY_ID)).thenReturn("GEOAPIFY");

        router.findById(GEOAPIFY_ID);

        verify(geoapify).findById(GEOAPIFY_ID);
        assertThat(router.providerCode(GEOAPIFY_ID)).isEqualTo("GEOAPIFY");
    }

    @Test
    void Google_장소는_선택과_저장_조회를_구분한다() {
        when(google.isConfigured()).thenReturn(true);

        router.findForSelection(GOOGLE_ID, "session-1");
        router.findById(GOOGLE_ID);

        verify(google).findForSelection(GOOGLE_ID, "session-1");
        verify(google).findById(GOOGLE_ID);
        assertThat(router.providerCode(GOOGLE_ID)).isEqualTo("GOOGLE");
    }
}
