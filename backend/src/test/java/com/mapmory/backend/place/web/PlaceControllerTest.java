package com.mapmory.backend.place.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.place.application.PlaceSearchService;
import com.mapmory.backend.place.application.PlaceSelectionService;
import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.model.SelectedPlace;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import static org.mockito.Mockito.mock;

class PlaceControllerTest {

    @Test
    void 검색_후보를_응답한다() throws Exception {
        PlaceSearchService searchService = mock(PlaceSearchService.class);
        PlaceSelectionService selectionService = mock(PlaceSelectionService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PlaceController(searchService, selectionService)).build();
        String token = "0b6f8f4e-5d0a-4c9a-9f43-0c1c2f5f2b11";
        when(searchService.search("한강공원", token))
                .thenReturn(List.of(new PlaceCandidate("park-1", "여의도한강공원", "서울 영등포구", null, null)));

        mockMvc.perform(get("/api/v1/places/search").param("query", "한강공원").param("sessionToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].placeId").value("park-1"))
                .andExpect(jsonPath("$.data[0].name").value("여의도한강공원"));
    }

    @Test
    void 세션_토큰_없는_검색은_거절한다() throws Exception {
        PlaceSearchService searchService = mock(PlaceSearchService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PlaceController(searchService, mock(PlaceSelectionService.class)))
                .build();

        mockMvc.perform(get("/api/v1/places/search").param("query", "한강공원"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchService);
    }

    @Test
    void 세션_토큰을_검색과_선택에_넘긴다() throws Exception {
        PlaceSearchService searchService = mock(PlaceSearchService.class);
        PlaceSelectionService selectionService = mock(PlaceSelectionService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PlaceController(searchService, selectionService)).build();
        String token = "0b6f8f4e-5d0a-4c9a-9f43-0c1c2f5f2b11";
        when(searchService.search("경복", token)).thenReturn(List.of());
        when(selectionService.select("ChIJ-gyeong", token))
                .thenReturn(new SelectedPlace(
                        new PlaceDetails("ChIJ-gyeong", null, "KR", null, null, List.of()), null));

        mockMvc.perform(get("/api/v1/places/search").param("query", "경복").param("sessionToken", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/places/ChIJ-gyeong").param("sessionToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").doesNotExist());

        verify(searchService).search("경복", token);
        verify(selectionService).select("ChIJ-gyeong", token);
    }

    @Test
    void Google이_받지_않는_36자_초과_세션_토큰은_거절한다() {
        PlaceSearchService searchService = mock(PlaceSearchService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProxyFactory proxyFactory = new ProxyFactory(new PlaceController(searchService, mock(PlaceSelectionService.class)));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new MethodValidationInterceptor((jakarta.validation.Validator) validator));
        PlaceController controller = (PlaceController) proxyFactory.getProxy();

        assertThatThrownBy(() -> controller.search("경복", "a".repeat(37)))
                .isInstanceOf(ConstraintViolationException.class);
        controller.search("경복", "a".repeat(36));
    }

    @Test
    void 선택한_장소의_추천_지역을_응답한다() throws Exception {
        PlaceSearchService searchService = mock(PlaceSearchService.class);
        PlaceSelectionService selectionService = mock(PlaceSelectionService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PlaceController(searchService, selectionService)).build();
        when(selectionService.select("ChIJ-park", null))
                .thenReturn(new SelectedPlace(
                        new PlaceDetails("ChIJ-park", null, "KR", null, null, List.of()), null));

        mockMvc.perform(get("/api/v1/places/ChIJ-park"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placeId").value("ChIJ-park"))
                .andExpect(jsonPath("$.data.manualRegionRequired").value(true));
    }
}
