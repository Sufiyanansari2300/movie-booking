package com.sufiyan.moviebooking.service;

import com.sufiyan.moviebooking.dto.MovieRequest;
import com.sufiyan.moviebooking.dto.MovieResponse;
import com.sufiyan.moviebooking.dto.PageResponse;
import com.sufiyan.moviebooking.entity.Movie;
import com.sufiyan.moviebooking.exception.ConflictException;
import com.sufiyan.moviebooking.exception.ResourceNotFoundException;
import com.sufiyan.moviebooking.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MovieService {

    private static final Set<String> SORTABLE = new LinkedHashSet<>(
            List.of("title", "releaseDate", "durationMinutes", "language", "genre"));

    private final MovieRepository movieRepository;

    @Transactional
    public MovieResponse create(MovieRequest request) {
        String title = request.title().trim();
        String language = request.language().trim();
        if (movieRepository.existsByTitleIgnoreCaseAndLanguageIgnoreCase(title, language)) {
            throw duplicate(title, language);
        }
        Movie movie = new Movie(title, blankToNull(request.description()), language, request.genre().trim(),
                request.durationMinutes(), blankToNull(request.certificate()), request.releaseDate());
        return MovieResponse.from(movieRepository.save(movie));
    }

    @Transactional
    public MovieResponse update(Long id, MovieRequest request) {
        Movie movie = getEntity(id);
        String title = request.title().trim();
        String language = request.language().trim();
        if (movieRepository.existsByTitleIgnoreCaseAndLanguageIgnoreCaseAndIdNot(title, language, id)) {
            throw duplicate(title, language);
        }
        movie.setTitle(title);
        movie.setDescription(blankToNull(request.description()));
        movie.setLanguage(language);
        movie.setGenre(request.genre().trim());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setCertificate(blankToNull(request.certificate()));
        movie.setReleaseDate(request.releaseDate());
        return MovieResponse.from(movie);
    }

    @Transactional
    public void delete(Long id) {
        movieRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public MovieResponse get(Long id) {
        return MovieResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<MovieResponse> search(String title, String language, String genre, Pageable pageable) {
        SortValidator.requireAllowed(pageable, SORTABLE);
        return PageResponse.from(
                movieRepository.search(blankToNull(title), blankToNull(language), blankToNull(genre), pageable),
                MovieResponse::from);
    }

    Movie getEntity(Long id) {
        return movieRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Movie", id));
    }

    private static ConflictException duplicate(String title, String language) {
        return new ConflictException("MOVIE_ALREADY_EXISTS", "Movie already exists: " + title + " (" + language + ")");
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
