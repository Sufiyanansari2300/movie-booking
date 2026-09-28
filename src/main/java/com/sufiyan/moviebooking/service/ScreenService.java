package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.ScreenRequest;
import com.sufiyan.moviebooking.dto.ScreenResponse;
import com.sufiyan.moviebooking.dto.SeatLayoutRequest;
import com.sufiyan.moviebooking.dto.SeatLayoutResponse;
import com.sufiyan.moviebooking.entity.Screen;
import com.sufiyan.moviebooking.entity.Seat;
import com.sufiyan.moviebooking.entity.SeatType;
import com.sufiyan.moviebooking.entity.Theater;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.ScreenRepository;
import com.sufiyan.moviebooking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScreenService {

    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final TheaterService theaterService;

    @Transactional
    public ScreenResponse create(Long theaterId, ScreenRequest request) {
        Theater theater = theaterService.getEntity(theaterId);
        String name = request.name().trim();
        if (screenRepository.existsByTheaterIdAndNameIgnoreCase(theaterId, name)) {
            throw new ConflictException("SCREEN_ALREADY_EXISTS", "Screen already exists in this theater: " + name);
        }
        return ScreenResponse.from(screenRepository.save(new Screen(theater, name)), 0);
    }

    @Transactional
    public ScreenResponse rename(Long id, ScreenRequest request) {
        Screen screen = getEntity(id);
        String name = request.name().trim();
        if (screenRepository.existsByTheaterIdAndNameIgnoreCaseAndIdNot(screen.getTheater().getId(), name, id)) {
            throw new ConflictException("SCREEN_ALREADY_EXISTS", "Screen already exists in this theater: " + name);
        }
        screen.setName(name);
        return ScreenResponse.from(screen, seatRepository.countByScreenId(id));
    }

    /** Deletes the screen and its seat layout. */
    @Transactional
    public void delete(Long id) {
        Screen screen = getEntity(id);
        seatRepository.deleteByScreenId(id);
        screenRepository.delete(screen);
    }

    @Transactional(readOnly = true)
    public List<ScreenResponse> listByTheater(Long theaterId) {
        theaterService.getEntity(theaterId);
        Map<Long, Long> counts = seatRepository.countByScreenForTheater(theaterId).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return screenRepository.findByTheaterIdOrderByNameAsc(theaterId).stream()
                .map(s -> ScreenResponse.from(s, counts.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    /** Replaces the whole seat layout of a screen. */
    @Transactional
    public SeatLayoutResponse replaceLayout(Long screenId, SeatLayoutRequest request) {
        Screen screen = getEntity(screenId);
        List<SeatLayoutPlanner.PlannedSeat> planned = SeatLayoutPlanner.plan(request);
        seatRepository.deleteByScreenId(screenId);
        // The bulk delete cleared the persistence context, so attach new seats to a fresh reference.
        Screen screenRef = screenRepository.getReferenceById(screenId);
        seatRepository.saveAll(planned.stream()
                .map(p -> new Seat(screenRef, p.row(), p.number(), p.seatType()))
                .toList());
        return layout(screen, seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screenId));
    }

    @Transactional(readOnly = true)
    public SeatLayoutResponse getLayout(Long screenId) {
        Screen screen = getEntity(screenId);
        return layout(screen, seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screenId));
    }

    Screen getEntity(Long id) {
        return screenRepository.findWithTheaterById(id).orElseThrow(() -> new ResourceNotFoundException("Screen", id));
    }

    private static SeatLayoutResponse layout(Screen screen, List<Seat> seats) {
        Map<SeatType, Long> byType = new EnumMap<>(SeatType.class);
        Map<String, List<Seat>> byRow = new LinkedHashMap<>();
        for (Seat seat : seats) {
            byType.merge(seat.getSeatType(), 1L, Long::sum);
            byRow.computeIfAbsent(seat.getRowLabel(), r -> new ArrayList<>()).add(seat);
        }
        List<SeatLayoutResponse.Row> rows = byRow.entrySet().stream()
                .map(e -> new SeatLayoutResponse.Row(e.getKey(), e.getValue().getFirst().getSeatType(),
                        e.getValue().stream()
                                .map(s -> new SeatLayoutResponse.SeatInfo(s.getId(), s.getLabel(), s.getSeatNumber()))
                                .toList()))
                .toList();
        return new SeatLayoutResponse(screen.getId(), screen.getName(), seats.size(), byType, rows);
    }
}
