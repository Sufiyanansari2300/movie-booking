package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.TheaterRequest;
import com.sufiyan.moviebooking.dto.TheaterResponse;
import com.sufiyan.moviebooking.entity.City;
import com.sufiyan.moviebooking.entity.Theater;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TheaterService {

    private final TheaterRepository theaterRepository;
    private final CityService cityService;

    @Transactional
    public TheaterResponse create(TheaterRequest request) {
        City city = cityService.getEntity(request.cityId());
        String name = request.name().trim();
        if (theaterRepository.existsByCityIdAndNameIgnoreCase(city.getId(), name)) {
            throw new ConflictException("THEATER_ALREADY_EXISTS", "Theater already exists in " + city.getName() + ": " + name);
        }
        return TheaterResponse.from(theaterRepository.save(new Theater(city, name, request.address().trim())));
    }

    @Transactional
    public TheaterResponse update(Long id, TheaterRequest request) {
        Theater theater = getEntity(id);
        City city = cityService.getEntity(request.cityId());
        String name = request.name().trim();
        if (theaterRepository.existsByCityIdAndNameIgnoreCaseAndIdNot(city.getId(), name, id)) {
            throw new ConflictException("THEATER_ALREADY_EXISTS", "Theater already exists in " + city.getName() + ": " + name);
        }
        theater.setCity(city);
        theater.setName(name);
        theater.setAddress(request.address().trim());
        return TheaterResponse.from(theater);
    }

    @Transactional
    public void delete(Long id) {
        theaterRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public TheaterResponse get(Long id) {
        return TheaterResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<TheaterResponse> listByCity(Long cityId) {
        cityService.getEntity(cityId); // 404 for an unknown city rather than an empty list
        return theaterRepository.findByCityIdOrderByNameAsc(cityId).stream().map(TheaterResponse::from).toList();
    }

    Theater getEntity(Long id) {
        return theaterRepository.findWithCityById(id).orElseThrow(() -> new ResourceNotFoundException("Theater", id));
    }
}
