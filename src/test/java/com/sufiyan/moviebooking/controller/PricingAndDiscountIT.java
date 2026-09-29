package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.Booking;
import com.sufiyan.moviebooking.entity.DiscountCode;
import com.sufiyan.moviebooking.entity.DiscountRedemption;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.repository.BookingRepository;
import com.sufiyan.moviebooking.repository.DiscountCodeRepository;
import com.sufiyan.moviebooking.repository.DiscountRedemptionRepository;
import com.sufiyan.moviebooking.support.MutableClock;
import com.sufiyan.moviebooking.support.MutableClockConfig;
import com.sufiyan.moviebooking.support.TestData;
import com.sufiyan.moviebooking.support.TestData.ShowFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Transactional
class PricingAndDiscountIT {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String WEEKEND_RULE = """
            {"name": "Weekend", "ruleType": "WEEKEND", "adjustmentPercent": 20}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext ctx;
    @Autowired private MutableClock clock;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private DiscountCodeRepository discountCodeRepository;
    @Autowired private DiscountRedemptionRepository redemptionRepository;

    private TestData data;
    private User aliceUser;
    private String alice;
    private String bob;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        data = new TestData(ctx);
        aliceUser = data.user(Role.CUSTOMER);
        alice = login(mockMvc, aliceUser.getEmail(), TestData.PASSWORD);
        bob = login(mockMvc, data.user(Role.CUSTOMER).getEmail(), TestData.PASSWORD);
        admin = login(mockMvc, data.user(Role.ADMIN).getEmail(), TestData.PASSWORD);
    }

    // ---------- pricing rules ----------

    @Test
    void weekendRule_raisesPricesOnWeekendsOnly_everywhereCustomersSeeThem() throws Exception {
        adminPost("/api/admin/pricing-rules", WEEKEND_RULE).andExpect(status().isCreated());
        ShowFixture saturday = data.show(next(DayOfWeek.SATURDAY, 13));
        ShowFixture wednesday = data.show(next(DayOfWeek.WEDNESDAY, 13));

        mockMvc.perform(get("/api/shows/{id}", saturday.showId()))
                .andExpect(jsonPath("$.prices.REGULAR").value(200.00))
                .andExpect(jsonPath("$.effectivePrices.REGULAR").value(240.00))
                .andExpect(jsonPath("$.effectivePrices.PREMIUM").value(420.00))
                .andExpect(jsonPath("$.appliedPricingRules", contains("Weekend")));
        mockMvc.perform(get("/api/shows/{id}/seats", saturday.showId()))
                .andExpect(jsonPath("$.appliedPricingRules", contains("Weekend")))
                .andExpect(jsonPath("$.rows[0].basePrice").value(200.00))
                .andExpect(jsonPath("$.rows[0].price").value(240.00));
        mockMvc.perform(get("/api/shows/{id}", wednesday.showId()))
                .andExpect(jsonPath("$.effectivePrices.REGULAR").value(200.00))
                .andExpect(jsonPath("$.appliedPricingRules", empty()));

        hold(alice, saturday, saturday.seat("A1"), saturday.seat("C1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedPricingRules", contains("Weekend")))
                .andExpect(jsonPath("$.seats[0].basePrice").value(200.00))
                .andExpect(jsonPath("$.seats[0].price").value(240.00))
                .andExpect(jsonPath("$.seats[1].price").value(420.00))
                .andExpect(jsonPath("$.subtotalAmount").value(660.00))
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(660.00));
    }

    @Test
    void weekendAndPrimeTime_addUp() throws Exception {
        adminPost("/api/admin/pricing-rules", WEEKEND_RULE).andExpect(status().isCreated());
        adminPost("/api/admin/pricing-rules", """
                {"name": "Prime time", "ruleType": "PRIME_TIME", "adjustmentPercent": 10,
                 "windowStart": "18:00", "windowEnd": "22:00"}
                """).andExpect(status().isCreated());
        ShowFixture saturdayEvening = data.show(next(DayOfWeek.SATURDAY, 19));

        mockMvc.perform(get("/api/shows/{id}", saturdayEvening.showId()))
                .andExpect(jsonPath("$.effectivePrices.REGULAR").value(260.00))
                .andExpect(jsonPath("$.appliedPricingRules", contains("Prime time", "Weekend")));
    }

    @Test
    void ruleChanges_doNotRepriceExistingHolds() throws Exception {
        String body = adminPost("/api/admin/pricing-rules", WEEKEND_RULE).andReturn().getResponse().getContentAsString();
        long ruleId = ((Number) JsonPath.read(body, "$.id")).longValue();
        ShowFixture saturday = data.show(next(DayOfWeek.SATURDAY, 13));
        long bookingId = idOf(hold(alice, saturday, saturday.seat("A1")));

        mockMvc.perform(put("/api/admin/pricing-rules/{id}", ruleId).with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Weekend\", \"ruleType\": \"WEEKEND\", \"adjustmentPercent\": 50}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/{id}", bookingId).with(bearer(alice)))
                .andExpect(jsonPath("$.totalAmount").value(240.00));
        mockMvc.perform(get("/api/shows/{id}", saturday.showId()))
                .andExpect(jsonPath("$.effectivePrices.REGULAR").value(300.00));
    }

    @Test
    void invalidPricingRules_areRejected() throws Exception {
        adminPost("/api/admin/pricing-rules", """
                {"name": "Prime", "ruleType": "PRIME_TIME", "adjustmentPercent": 10}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_PRICING_RULE"));
        adminPost("/api/admin/pricing-rules", """
                {"name": "Prime", "ruleType": "PRIME_TIME", "adjustmentPercent": 10,
                 "windowStart": "22:00", "windowEnd": "18:00"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_PRICING_RULE"));
        adminPost("/api/admin/pricing-rules", """
                {"name": "Weekend", "ruleType": "WEEKEND", "adjustmentPercent": 10, "windowStart": "10:00"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_PRICING_RULE"));
        adminPost("/api/admin/pricing-rules", """
                {"name": "Zero", "ruleType": "WEEKEND", "adjustmentPercent": 0}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_PRICING_RULE"));
        adminPost("/api/admin/pricing-rules", """
                {"name": "Huge", "ruleType": "WEEKEND", "adjustmentPercent": 1000}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        adminPost("/api/admin/pricing-rules", WEEKEND_RULE).andExpect(status().isCreated());
        adminPost("/api/admin/pricing-rules", WEEKEND_RULE)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PRICING_RULE_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/admin/pricing-rules").with(bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(WEEKEND_RULE))
                .andExpect(status().isForbidden());
    }

    // ---------- discount code administration ----------

    @Test
    void discountCodeAdmin_createsNormalizedCodes_andValidatesInput() throws Exception {
        adminPost("/api/admin/discount-codes", """
                {"code": "welcome10", "discountType": "PERCENT", "discountValue": 10, "maxDiscountAmount": 100}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WELCOME10"))
                .andExpect(jsonPath("$.usedCount").value(0))
                .andExpect(jsonPath("$.active").value(true));
        adminPost("/api/admin/discount-codes", """
                {"code": "WELCOME10", "discountType": "FLAT", "discountValue": 50}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DISCOUNT_ALREADY_EXISTS"));

        adminPost("/api/admin/discount-codes", """
                {"code": "TOOMUCH", "discountType": "PERCENT", "discountValue": 120}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_DISCOUNT"));
        adminPost("/api/admin/discount-codes", """
                {"code": "FLATCAP", "discountType": "FLAT", "discountValue": 50, "maxDiscountAmount": 20}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_DISCOUNT"));
        adminPost("/api/admin/discount-codes", """
                {"code": "BACKWARDS", "discountType": "FLAT", "discountValue": 50,
                 "validFrom": "2026-12-01T00:00:00+05:30", "validUntil": "2026-11-01T00:00:00+05:30"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_DISCOUNT"));
        adminPost("/api/admin/discount-codes", """
                {"code": "no spaces!", "discountType": "FLAT", "discountValue": 50}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/admin/discount-codes").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("WELCOME10")));
        mockMvc.perform(get("/api/admin/discount-codes").with(bearer(alice)))
                .andExpect(status().isForbidden());
    }

    @Test
    void discountCodes_canOnlyBeDeletedWhileUnused() throws Exception {
        long unused = createCode("{\"code\": \"UNUSED\", \"discountType\": \"FLAT\", \"discountValue\": 50}");
        long used = createCode("{\"code\": \"USED\", \"discountType\": \"FLAT\", \"discountValue\": 50}");
        ShowFixture show = data.show(next(DayOfWeek.WEDNESDAY, 13));
        long bookingId = idOf(hold(alice, show, show.seat("A1")));
        applyCode(alice, bookingId, "USED").andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/discount-codes/{id}", unused).with(bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/discount-codes/{id}", used).with(bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DISCOUNT_IN_USE"));
    }

    // ---------- applying codes to bookings ----------

    @Test
    void applyingCodes_updatesTheBreakdown_andCanBeReplacedOrRemoved() throws Exception {
        createCode("{\"code\": \"WELCOME10\", \"discountType\": \"PERCENT\", \"discountValue\": 10}");
        createCode("{\"code\": \"FLAT50\", \"discountType\": \"FLAT\", \"discountValue\": 50}");
        ShowFixture show = data.show(next(DayOfWeek.WEDNESDAY, 13));
        long bookingId = idOf(hold(alice, show, show.seat("A1"), show.seat("A2"), show.seat("A3"))); // 600

        applyCode(alice, bookingId, " welcome10 ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCode").value("WELCOME10"))
                .andExpect(jsonPath("$.subtotalAmount").value(600.00))
                .andExpect(jsonPath("$.discountAmount").value(60.00))
                .andExpect(jsonPath("$.totalAmount").value(540.00));

        applyCode(alice, bookingId, "FLAT50")
                .andExpect(jsonPath("$.discountCode").value("FLAT50"))
                .andExpect(jsonPath("$.discountAmount").value(50.00))
                .andExpect(jsonPath("$.totalAmount").value(550.00));

        mockMvc.perform(delete("/api/bookings/{id}/discount", bookingId).with(bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCode").doesNotExist())
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(600.00));
    }

    @Test
    void codesThatCannotBeUsed_areRejectedWithAReason() throws Exception {
        ShowFixture show = data.show(next(DayOfWeek.WEDNESDAY, 13));
        long bookingId = idOf(hold(alice, show, show.seat("A1"))); // 200

        applyCode(alice, bookingId, "NOPE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISCOUNT_NOT_FOUND"));

        createCode("{\"code\": \"BIGORDER\", \"discountType\": \"FLAT\", \"discountValue\": 50, \"minOrderAmount\": 500}");
        applyCode(alice, bookingId, "BIGORDER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISCOUNT_MIN_ORDER_NOT_MET"));

        createCode("{\"code\": \"OFF\", \"discountType\": \"FLAT\", \"discountValue\": 50, \"active\": false}");
        applyCode(alice, bookingId, "OFF").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISCOUNT_INACTIVE"));

        String past = OffsetDateTime.now(IST).minusDays(1).toString();
        createCode("{\"code\": \"OLD\", \"discountType\": \"FLAT\", \"discountValue\": 50, \"validUntil\": \"" + past + "\"}");
        applyCode(alice, bookingId, "OLD").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISCOUNT_EXPIRED"));

        String future = OffsetDateTime.now(IST).plusDays(1).toString();
        createCode("{\"code\": \"SOON\", \"discountType\": \"FLAT\", \"discountValue\": 50, \"validFrom\": \"" + future + "\"}");
        applyCode(alice, bookingId, "SOON").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISCOUNT_NOT_YET_VALID"));

        long exhaustedId = createCode("{\"code\": \"GONE\", \"discountType\": \"FLAT\", \"discountValue\": 50, \"usageLimit\": 2}");
        discountCodeRepository.findById(exhaustedId).orElseThrow().setUsedCount(2);
        applyCode(alice, bookingId, "GONE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISCOUNT_EXHAUSTED"));

        long oncePerUser = createCode("{\"code\": \"ONCE\", \"discountType\": \"FLAT\", \"discountValue\": 50, \"perUserLimit\": 1}");
        recordPastRedemption(oncePerUser);
        applyCode(alice, bookingId, "ONCE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DISCOUNT_USER_LIMIT_REACHED"));
        applyCode(bob, idOf(hold(bob, show, show.seat("B1"))), "ONCE").andExpect(status().isOk());
    }

    @Test
    void codesCanOnlyBeAppliedToYourOwnActiveHold() throws Exception {
        createCode("{\"code\": \"FLAT50\", \"discountType\": \"FLAT\", \"discountValue\": 50}");
        ShowFixture show = data.show(next(DayOfWeek.WEDNESDAY, 13));
        long bookingId = idOf(hold(alice, show, show.seat("A1")));

        applyCode(bob, bookingId, "FLAT50").andExpect(status().isNotFound());

        clock.advance(Duration.ofMinutes(11));
        applyCode(alice, bookingId, "FLAT50").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("HOLD_EXPIRED"));

        clock.reset();
        mockMvc.perform(delete("/api/bookings/{id}/hold", bookingId).with(bearer(alice))).andExpect(status().isOk());
        applyCode(alice, bookingId, "FLAT50").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_HELD"));
    }

    // ---------- helpers ----------

    /** An earlier booking by Alice (another show) that used the code, as phase 6 confirmation will record it. */
    private void recordPastRedemption(long codeId) throws Exception {
        ShowFixture earlierShow = data.show(next(DayOfWeek.THURSDAY, 13));
        long pastBooking = idOf(hold(alice, earlierShow, earlierShow.seat("A1")));
        mockMvc.perform(delete("/api/bookings/{id}/hold", pastBooking).with(bearer(alice))).andExpect(status().isOk());
        Booking booking = bookingRepository.findById(pastBooking).orElseThrow();
        DiscountCode code = discountCodeRepository.findById(codeId).orElseThrow();
        redemptionRepository.save(new DiscountRedemption(code, booking, aliceUser, new BigDecimal("50.00")));
    }

    private static OffsetDateTime next(DayOfWeek day, int hour) {
        return LocalDate.now(IST).with(TemporalAdjusters.next(day)).atTime(hour, 0).atZone(IST).toOffsetDateTime();
    }

    private ResultActions adminPost(String path, String json) throws Exception {
        return mockMvc.perform(post(path).with(bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createCode(String json) throws Exception {
        String body = adminPost("/api/admin/discount-codes", json)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions applyCode(String token, long bookingId, String code) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/discount", bookingId).with(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"" + code + "\"}"));
    }

    private ResultActions hold(String token, ShowFixture show, long... seats) throws Exception {
        String ids = LongStream.of(seats).mapToObj(String::valueOf).collect(Collectors.joining(", "));
        return mockMvc.perform(post("/api/shows/{id}/holds", show.showId()).with(bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"showSeatIds\": [" + ids + "]}"));
    }

    private static long idOf(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
