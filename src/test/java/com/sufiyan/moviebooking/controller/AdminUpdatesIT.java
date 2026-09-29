package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.repository.DiscountCodeRepository;
import com.sufiyan.moviebooking.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Update/rename paths and HTTP-level errors of the admin APIs. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUpdatesIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext ctx;
    @Autowired private DiscountCodeRepository discountCodeRepository;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(mockMvc, new TestData(ctx).user(Role.ADMIN).getEmail(), TestData.PASSWORD);
    }

    @Test
    void theaters_canBeRenamedAndMovedToAnotherCity_butNotIntoADuplicate() throws Exception {
        long pune = id(send("POST", "/api/admin/cities", "{\"name\": \"Pune\", \"state\": \"MH\"}"));
        long mumbai = id(send("POST", "/api/admin/cities", "{\"name\": \"Mumbai\", \"state\": \"MH\"}"));
        long a = id(send("POST", "/api/admin/theaters", theater(pune, "Alpha")));
        id(send("POST", "/api/admin/theaters", theater(mumbai, "Beta")));

        send("PUT", "/api/admin/theaters/" + a, theater(mumbai, "Alpha Prime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Prime"))
                .andExpect(jsonPath("$.city.name").value("Mumbai"));
        send("PUT", "/api/admin/theaters/" + a, theater(mumbai, "beta"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("THEATER_ALREADY_EXISTS"));
        send("PUT", "/api/admin/theaters/999999", theater(mumbai, "X")).andExpect(status().isNotFound());
        send("PUT", "/api/admin/cities/" + pune, "{\"name\": \"MUMBAI\", \"state\": \"MH\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CITY_ALREADY_EXISTS"));
    }

    @Test
    void screens_canBeRenamed_butNotToADuplicateName() throws Exception {
        long city = id(send("POST", "/api/admin/cities", "{\"name\": \"Pune\", \"state\": \"MH\"}"));
        long theaterId = id(send("POST", "/api/admin/theaters", theater(city, "Alpha")));
        long audi1 = id(send("POST", "/api/admin/theaters/" + theaterId + "/screens", "{\"name\": \"Audi 1\"}"));
        id(send("POST", "/api/admin/theaters/" + theaterId + "/screens", "{\"name\": \"Audi 2\"}"));

        send("PUT", "/api/admin/screens/" + audi1, "{\"name\": \"IMAX\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("IMAX"));
        send("PUT", "/api/admin/screens/" + audi1, "{\"name\": \"audi 2\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SCREEN_ALREADY_EXISTS"));
    }

    @Test
    void movies_cannotBeRenamedIntoAnExistingTitleAndLanguage() throws Exception {
        long a = id(send("POST", "/api/admin/movies", movie("Dune")));
        id(send("POST", "/api/admin/movies", movie("Arrival")));

        send("PUT", "/api/admin/movies/" + a, movie("ARRIVAL"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("MOVIE_ALREADY_EXISTS"));
    }

    @Test
    void pricingRules_listUpdateDelete() throws Exception {
        long rule = id(send("POST", "/api/admin/pricing-rules",
                "{\"name\": \"Weekend\", \"ruleType\": \"WEEKEND\", \"adjustmentPercent\": 20}"));
        id(send("POST", "/api/admin/pricing-rules",
                "{\"name\": \"Matinee\", \"ruleType\": \"PRIME_TIME\", \"adjustmentPercent\": -10, "
                        + "\"windowStart\": \"09:00\", \"windowEnd\": \"12:00\"}"));

        send("PUT", "/api/admin/pricing-rules/" + rule,
                "{\"name\": \"Weekend\", \"ruleType\": \"WEEKEND\", \"adjustmentPercent\": 25, \"active\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjustmentPercent").value(25))
                .andExpect(jsonPath("$.active").value(false));
        send("PUT", "/api/admin/pricing-rules/" + rule,
                "{\"name\": \"matinee\", \"ruleType\": \"WEEKEND\", \"adjustmentPercent\": 25}")
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/admin/pricing-rules").with(bearer(admin)))
                .andExpect(jsonPath("$[*].name", hasItem("Matinee")));
        mockMvc.perform(delete("/api/admin/pricing-rules/{id}", rule).with(bearer(admin))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/pricing-rules/{id}", rule).with(bearer(admin))).andExpect(status().isNotFound());
    }

    @Test
    void discountCodes_getAndUpdate_withGuards() throws Exception {
        long id = id(send("POST", "/api/admin/discount-codes",
                "{\"code\": \"SAVE20\", \"discountType\": \"PERCENT\", \"discountValue\": 20, \"usageLimit\": 10}"));
        DiscountCode saved = discountCodeRepository.findById(id).orElseThrow();
        saved.setUsedCount(4);

        mockMvc.perform(get("/api/admin/discount-codes/{id}", id).with(bearer(admin)))
                .andExpect(jsonPath("$.code").value("SAVE20")).andExpect(jsonPath("$.usedCount").value(4));
        send("PUT", "/api/admin/discount-codes/" + id,
                "{\"code\": \"SAVE20\", \"discountType\": \"FLAT\", \"discountValue\": 75, \"usageLimit\": 20, "
                        + "\"validUntil\": \"2030-01-01T00:00:00+05:30\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountType").value("FLAT"))
                .andExpect(jsonPath("$.discountValue").value(75))
                .andExpect(jsonPath("$.validUntil").value("2030-01-01T00:00:00+05:30"));
        send("PUT", "/api/admin/discount-codes/" + id,
                "{\"code\": \"OTHER\", \"discountType\": \"FLAT\", \"discountValue\": 75}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_DISCOUNT"));
        send("PUT", "/api/admin/discount-codes/" + id,
                "{\"code\": \"SAVE20\", \"discountType\": \"FLAT\", \"discountValue\": 75, \"usageLimit\": 3}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                        containsString("4 uses")));
        mockMvc.perform(get("/api/admin/discount-codes/{id}", 999999).with(bearer(admin))).andExpect(status().isNotFound());
    }

    @Test
    void refundPolicies_renameConflicts_activeOneCannotBeDeleted_unknownIs404() throws Exception {
        long active = id(send("POST", "/api/admin/refund-policies",
                "{\"name\": \"A\", \"active\": true, \"rules\": [{\"minHoursBeforeShow\": 24, \"refundPercent\": 50}]}"));
        long other = id(send("POST", "/api/admin/refund-policies",
                "{\"name\": \"B\", \"rules\": [{\"minHoursBeforeShow\": 24, \"refundPercent\": 50}]}"));

        send("PUT", "/api/admin/refund-policies/" + other,
                "{\"name\": \"a\", \"rules\": [{\"minHoursBeforeShow\": 24, \"refundPercent\": 50}]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REFUND_POLICY_ALREADY_EXISTS"));
        send("POST", "/api/admin/refund-policies",
                "{\"name\": \"b\", \"rules\": [{\"minHoursBeforeShow\": 1, \"refundPercent\": 10}]}")
                .andExpect(status().isConflict());
        send("PUT", "/api/admin/refund-policies/" + other,
                "{\"name\": \"B2\", \"active\": true, \"rules\": [{\"minHoursBeforeShow\": 12, \"refundPercent\": 80}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(delete("/api/admin/refund-policies/{id}", other).with(bearer(admin)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REFUND_POLICY_ACTIVE"));
        mockMvc.perform(delete("/api/admin/refund-policies/{id}", active).with(bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/refund-policies/{id}/activate", 999999).with(bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongHttpMethodAndContentType_areClearErrors() throws Exception {
        // Authenticated, so the request gets past security and reaches routing
        mockMvc.perform(delete("/api/cities").with(bearer(admin)))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(get("/api/no-such-endpoint").with(bearer(admin)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private ResultActions send(String method, String path, String json) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(path);
            case "PUT" -> put(path);
            default -> throw new IllegalArgumentException(method);
        };
        return mockMvc.perform(builder.with(bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String theater(long cityId, String name) {
        return "{\"cityId\": %d, \"name\": \"%s\", \"address\": \"Somewhere\"}".formatted(cityId, name);
    }

    private static String movie(String title) {
        return "{\"title\": \"%s\", \"language\": \"English\", \"genre\": \"Sci-Fi\", \"durationMinutes\": 150}"
                .formatted(title);
    }

    private static long id(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
