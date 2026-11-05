package com.mapmory.backend.region.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.region.application.RegionBoundaryService;
import com.mapmory.backend.region.domain.RegionBoundary;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

class RegionControllerTest {

    @Test
    void 나라_경계를_버전과_함께_오래_캐시하도록_응답한다() throws Exception {
        RegionBoundaryService service = mock(RegionBoundaryService.class);
        when(service.countryBoundary("HK")).thenReturn(new RegionBoundary(
                "HK", "v1", List.of(List.of(List.of(114.1, 22.2), List.of(114.2, 22.3), List.of(114.1, 22.2)))));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegionController(service)).build();

        mockMvc.perform(get("/api/v1/regions/HK/boundary"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=2592000, private"))
                .andExpect(jsonPath("$.data.regionCode").value("HK"))
                .andExpect(jsonPath("$.data.version").value("v1"))
                .andExpect(jsonPath("$.data.rings[0][1][0]").value(114.2));
    }

    @Test
    void 대문자_두_글자가_아닌_코드는_거절한다() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProxyFactory proxyFactory = new ProxyFactory(new RegionController(mock(RegionBoundaryService.class)));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new MethodValidationInterceptor((jakarta.validation.Validator) validator));
        RegionController controller = (RegionController) proxyFactory.getProxy();

        assertThatThrownBy(() -> controller.countryBoundary("hk")).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> controller.countryBoundary("HKG")).isInstanceOf(ConstraintViolationException.class);
    }
}
