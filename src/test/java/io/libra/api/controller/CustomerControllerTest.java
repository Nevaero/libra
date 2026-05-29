package io.libra.api.controller;

import io.libra.customer.domain.Customer;
import io.libra.customer.domain.enums.ClientCategory;
import io.libra.customer.domain.enums.CustomerStatus;
import io.libra.customer.domain.enums.KycLevel;
import io.libra.customer.domain.enums.RiskProfile;
import io.libra.customer.port.CustomerService;
import io.libra.util.Uuids;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web-layer smoke test : routing, DTO/JSON round-trip, and the not-found path, with the port mocked
// and the security filter chain off (the real chain permits all anyway).
@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomerControllerTest.CacheConfig.class)
class CustomerControllerTest {

    // The app is @EnableCaching; the web slice excludes Boot's cache autoconfiguration, so supply a
    // no-op cache manager to satisfy the caching aspect.
    @TestConfiguration
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerService customerService;

    private Customer sample(UUID id) {
        return new Customer(id, "jane@libra.io", "Jane", "Doe", LocalDate.of(1990, 1, 1), "CH",
                CustomerStatus.PENDING_KYC, KycLevel.NONE, RiskProfile.BALANCED, ClientCategory.RETAIL,
                Instant.now(), null);
    }

    @Test
    void onboardReturns201AndBody() throws Exception {
        UUID id = Uuids.newId();
        given(customerService.onboard(any())).willReturn(sample(id));
        String body = """
                {"email":"jane@libra.io","firstName":"Jane","lastName":"Doe","birthDate":"1990-01-01",
                 "countryOfResidence":"CH","clientCategory":"RETAIL","riskProfile":"BALANCED"}
                """;

        mvc.perform(post("/api/customers").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("jane@libra.io"))
                .andExpect(jsonPath("$.status").value("PENDING_KYC"));
    }

    @Test
    void getReturnsCustomerWhenFound() throws Exception {
        UUID id = Uuids.newId();
        given(customerService.findById(id)).willReturn(Optional.of(sample(id)));

        mvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = Uuids.newId();
        given(customerService.findById(id)).willReturn(Optional.empty());

        mvc.perform(get("/api/customers/{id}", id)).andExpect(status().isNotFound());
    }
}
