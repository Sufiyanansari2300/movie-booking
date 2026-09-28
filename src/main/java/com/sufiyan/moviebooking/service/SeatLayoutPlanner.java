package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.SeatLayoutRequest;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.exception.BadRequestException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands a layout request (row ranges x seats per row) into individual seats. Pure logic, no persistence.
 * Rows are single letters A-Z; a section's {@code rows} is either one row ("K") or a range ("A-H").
 */
public final class SeatLayoutPlanner {

    public static final int MAX_SEATS_PER_SCREEN = 1000;

    private static final Pattern ROW_SPEC = Pattern.compile("^([A-Z])(?:\\s*-\\s*([A-Z]))?$");

    private SeatLayoutPlanner() {
    }

    public record PlannedSeat(String row, int number, SeatType seatType) {
    }

    public static List<PlannedSeat> plan(SeatLayoutRequest request) {
        List<PlannedSeat> seats = new ArrayList<>();
        Set<Character> usedRows = new HashSet<>();
        for (SeatLayoutRequest.Section section : request.sections()) {
            for (char row : expandRows(section.rows())) {
                if (!usedRows.add(row)) {
                    throw new BadRequestException("INVALID_LAYOUT", "Row " + row + " appears in more than one section");
                }
                for (int number = 1; number <= section.seatsPerRow(); number++) {
                    seats.add(new PlannedSeat(String.valueOf(row), number, section.seatType()));
                }
            }
        }
        if (seats.size() > MAX_SEATS_PER_SCREEN) {
            throw new BadRequestException("INVALID_LAYOUT",
                    "A screen can have at most " + MAX_SEATS_PER_SCREEN + " seats, got " + seats.size());
        }
        return seats;
    }

    static List<Character> expandRows(String spec) {
        Matcher m = ROW_SPEC.matcher(spec.trim().toUpperCase(Locale.ROOT));
        if (!m.matches()) {
            throw new BadRequestException("INVALID_LAYOUT",
                    "Invalid rows '" + spec + "': use a letter (\"K\") or a range (\"A-H\")");
        }
        char start = m.group(1).charAt(0);
        char end = m.group(2) == null ? start : m.group(2).charAt(0);
        if (end < start) {
            throw new BadRequestException("INVALID_LAYOUT", "Invalid rows '" + spec + "': range must be ascending");
        }
        List<Character> rows = new ArrayList<>();
        for (char c = start; c <= end; c++) {
            rows.add(c);
        }
        return rows;
    }
}
