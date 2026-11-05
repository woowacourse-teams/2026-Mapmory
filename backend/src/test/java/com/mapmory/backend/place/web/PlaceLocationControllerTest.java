package com.mapmory.backend.place.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.handler.BusinessExceptionHandler;
import com.mapmory.backend.common.handler.ValidationExceptionHandler;
import com.mapmory.backend.place.application.LocatedRegion;
import com.mapmory.backend.place.application.LocationRegionService;
import com.mapmory.backend.place.domain.Coordinate;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.region.domain.RegionAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlaceLocationControllerTest {

    private static final PlaceProvider GOOGLE =
            new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps");

    private final LocationRegionService service = mock(LocationRegionService.class);
    private final ProblemDetailFactory problemDetailFactory = new ProblemDetailFactory();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PlaceLocationController(service))
            .setControllerAdvice(new BusinessExceptionHandler(problemDetailFactory),
                    new ValidationExceptionHandler(problemDetailFactory))
            .build();

    @Test
    void 좌표를_본문으로_받아_추천_지역을_응답한다() throws Exception {
        when(service.locate(new Coordinate(35.8694, 128.6062))).thenReturn(new LocatedRegion(
                new RegionAddress("KR", "대구광역시", List.of("중구")), null, GOOGLE));

        mockMvc.perform(post("/api/v1/places/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":35.8694,"longitude":128.6062}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.countryCode").value("KR"))
                .andExpect(jsonPath("$.data.suggestedRegion").doesNotExist())
                .andExpect(jsonPath("$.data.manualRegionRequired").value(true))
                .andExpect(jsonPath("$.data.attribution").value("Google Maps"));

        verify(service).locate(any(Coordinate.class));
    }

    @Test
    void 범위를_벗어난_좌표는_400으로_거절한다() throws Exception {
        mockMvc.perform(post("/api/v1/places/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":91,"longitude":128.6}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATE"));

        verifyNoInteractions(service);
    }

    @Test
    void 좌표가_빠지면_400으로_거절한다() throws Exception {
        mockMvc.perform(post("/api/v1/places/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":35.87}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
