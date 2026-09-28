package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.CityRequest;
import com.sufiyan.moviebooking.dto.CityResponse;
import com.sufiyan.moviebooking.entity.City;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.CityRepository;
import com.sufiyan.moviebooking.repository.TheaterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;

    @Transactional
    public CityResponse create(CityRequest request) {
        String name = request.name().trim();
        if (cityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("CITY_ALREADY_EXISTS", "City already exists: " + name);
        }
        return CityResponse.from(cityRepository.save(new City(name, request.state().trim())));
    }

    @Transactional
    public CityResponse update(Long id, CityRequest request) {
        City city = getEntity(id);
        String name = request.name().trim();
        if (cityRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("CITY_ALREADY_EXISTS", "City already exists: " + name);
        }
        city.setName(name);
        city.setState(request.state().trim());
        return CityResponse.from(city);
    }

    @Transactional
    public void delete(Long id) {
        City city = getEntity(id);
        if (theaterRepository.existsByCityId(id)) {
            throw new ConflictException("CITY_HAS_THEATERS", "Delete the city's theaters first");
        }
        cityRepository.delete(city);
    }

    @Transactional(readOnly = true)
    public CityResponse get(Long id) {
        return CityResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<CityResponse> list() {
        return cityRepository.findAllByOrderByNameAsc().stream().map(CityResponse::from).toList();
    }

    City getEntity(Long id) {
        return cityRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("City", id));
    }
}
