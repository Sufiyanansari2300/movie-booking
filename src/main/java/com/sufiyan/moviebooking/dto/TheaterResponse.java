package com.sufiyan.moviebooking.dto;

import com.sufiyan.moviebooking.entity.Theater;

public record TheaterResponse(Long id, String name, String address, CityResponse city) {

    public static TheaterResponse from(Theater theater) {
        return new TheaterResponse(theater.getId(), theater.getName(), theater.getAddress(),
                CityResponse.from(theater.getCity()));
    }
}
