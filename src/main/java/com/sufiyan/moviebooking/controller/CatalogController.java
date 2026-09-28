package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.dto.CityResponse;
import com.sufiyan.moviebooking.dto.ScreenResponse;
import com.sufiyan.moviebooking.dto.TheaterResponse;
import com.sufiyan.moviebooking.service.CityService;
import com.sufiyan.moviebooking.service.ScreenService;
import com.sufiyan.moviebooking.service.TheaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, read-only browsing of cities, theaters and screens. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CityService cityService;
    private final TheaterService theaterService;
    private final ScreenService screenService;

    @GetMapping("/cities")
    public List<CityResponse> listCities() {
        return cityService.list();
    }

    @GetMapping("/cities/{id}")
    public CityResponse getCity(@PathVariable Long id) {
        return cityService.get(id);
    }

    @GetMapping("/cities/{id}/theaters")
    public List<TheaterResponse> listTheaters(@PathVariable Long id) {
        return theaterService.listByCity(id);
    }

    @GetMapping("/theaters/{id}")
    public TheaterResponse getTheater(@PathVariable Long id) {
        return theaterService.get(id);
    }

    @GetMapping("/theaters/{id}/screens")
    public List<ScreenResponse> listScreens(@PathVariable Long id) {
        return screenService.listByTheater(id);
    }
}
