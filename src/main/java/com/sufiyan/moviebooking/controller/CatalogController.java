package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.dto.CityResponse;
import com.sufiyan.moviebooking.dto.ScreenResponse;
import com.sufiyan.moviebooking.dto.TheaterResponse;
import com.sufiyan.moviebooking.service.CityService;
import com.sufiyan.moviebooking.service.ScreenService;
import com.sufiyan.moviebooking.service.TheaterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, read-only browsing of cities, theaters and screens. */
@Tag(name = "Browse - Cities & Theaters", description = "Public, no token needed")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CityService cityService;
    private final TheaterService theaterService;
    private final ScreenService screenService;

    @Operation(summary = "List all cities")
    @GetMapping("/cities")
    public List<CityResponse> listCities() {
        return cityService.list();
    }

    @Operation(summary = "Get a city")
    @GetMapping("/cities/{id}")
    public CityResponse getCity(@PathVariable Long id) {
        return cityService.get(id);
    }

    @Operation(summary = "List theaters in a city")
    @GetMapping("/cities/{id}/theaters")
    public List<TheaterResponse> listTheaters(@PathVariable Long id) {
        return theaterService.listByCity(id);
    }

    @Operation(summary = "Get a theater")
    @GetMapping("/theaters/{id}")
    public TheaterResponse getTheater(@PathVariable Long id) {
        return theaterService.get(id);
    }

    @Operation(summary = "List screens of a theater with seat counts")
    @GetMapping("/theaters/{id}/screens")
    public List<ScreenResponse> listScreens(@PathVariable Long id) {
        return screenService.listByTheater(id);
    }
}
