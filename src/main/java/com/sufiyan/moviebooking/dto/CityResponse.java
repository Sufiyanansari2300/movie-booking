package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.City;

public record CityResponse(Long id, String name, String state) {

    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getState());
    }
}
