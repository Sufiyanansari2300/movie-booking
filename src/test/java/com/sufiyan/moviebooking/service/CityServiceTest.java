package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.CityRequest;
import com.sufiyan.moviebooking.entity.City;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.CityRepository;
import com.sufiyan.moviebooking.repository.TheaterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

    @Mock
    private CityRepository cityRepository;

    @Mock
    private TheaterRepository theaterRepository;

    @InjectMocks
    private CityService cityService;

    @Test
    void create_trimsInput() {
        when(cityRepository.existsByNameIgnoreCase("Pune")).thenReturn(false);
        when(cityRepository.save(any(City.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = cityService.create(new CityRequest("  Pune ", " Maharashtra "));

        assertThat(response.name()).isEqualTo("Pune");
        assertThat(response.state()).isEqualTo("Maharashtra");
    }

    @Test
    void create_duplicateName_isRejected() {
        when(cityRepository.existsByNameIgnoreCase("Pune")).thenReturn(true);

        assertThatThrownBy(() -> cityService.create(new CityRequest("Pune", "Maharashtra")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("CITY_ALREADY_EXISTS");
        verify(cityRepository, never()).save(any());
    }

    @Test
    void delete_cityWithTheaters_isRejected() {
        when(cityRepository.findById(1L)).thenReturn(Optional.of(new City("Pune", "Maharashtra")));
        when(theaterRepository.existsByCityId(1L)).thenReturn(true);

        assertThatThrownBy(() -> cityService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("CITY_HAS_THEATERS");
        verify(cityRepository, never()).delete(any());
    }

    @Test
    void get_unknownCity_throwsNotFound() {
        when(cityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cityService.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
