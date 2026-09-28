package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.SeatLayoutRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest.Section;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatLayoutPlannerTest {

    @Test
    void expandsRangesAndSingleRows() {
        var seats = SeatLayoutPlanner.plan(new SeatLayoutRequest(List.of(
                new Section("A-C", 4, SeatType.REGULAR),
                new Section("d", 2, SeatType.PREMIUM))));

        assertThat(seats).hasSize(3 * 4 + 2);
        assertThat(seats.getFirst()).isEqualTo(new SeatLayoutPlanner.PlannedSeat("A", 1, SeatType.REGULAR));
        assertThat(seats.getLast()).isEqualTo(new SeatLayoutPlanner.PlannedSeat("D", 2, SeatType.PREMIUM));
        assertThat(seats).filteredOn(s -> s.seatType() == SeatType.PREMIUM).hasSize(2);
    }

    @Test
    void acceptsSpacesAroundRangeDash() {
        assertThat(SeatLayoutPlanner.expandRows(" A - C ")).containsExactly('A', 'B', 'C');
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1-5", "AA", "A-", "A-B-C", "a..c"})
    void rejectsMalformedRowSpec(String spec) {
        assertThatThrownBy(() -> SeatLayoutPlanner.expandRows(spec))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("INVALID_LAYOUT");
    }

    @Test
    void rejectsDescendingRange() {
        assertThatThrownBy(() -> SeatLayoutPlanner.expandRows("H-A"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ascending");
    }

    @Test
    void rejectsRowUsedInTwoSections() {
        assertThatThrownBy(() -> SeatLayoutPlanner.plan(new SeatLayoutRequest(List.of(
                new Section("A-E", 10, SeatType.REGULAR),
                new Section("E-F", 10, SeatType.PREMIUM)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Row E");
    }

    @Test
    void rejectsMoreThanMaxSeats() {
        // 26 rows x 50 seats = 1300 > 1000
        assertThatThrownBy(() -> SeatLayoutPlanner.plan(new SeatLayoutRequest(List.of(
                new Section("A-Z", 50, SeatType.REGULAR)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most");
    }
}
