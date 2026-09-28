package com.sufiyan.moviebooking.controller;

import com.jayway.jsonpath.JsonPath;
import com.sufiyan.moviebooking.entity.Movie;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.repository.MovieRepository;
import com.sufiyan.moviebooking.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovieIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private MovieRepository movieRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        userService.createUser("Admin", "movie-admin@example.com", "password123", Role.ADMIN);
        adminToken = login(mockMvc, "movie-admin@example.com", "password123");
    }

    @Test
    void adminCreatesUpdatesAndDeletesMovie() throws Exception {
        String body = mockMvc.perform(post("/api/admin/movies").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Inception", "description": "Dreams within dreams",
                                 "language": "English", "genre": "Sci-Fi", "durationMinutes": 148,
                                 "certificate": "UA", "releaseDate": "2010-07-16"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Inception"))
                .andExpect(jsonPath("$.releaseDate").value("2010-07-16"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(put("/api/admin/movies/{id}", id).with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Inception", "language": "English", "genre": "Thriller",
                                 "durationMinutes": 150}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre").value("Thriller"))
                .andExpect(jsonPath("$.durationMinutes").value(150))
                .andExpect(jsonPath("$.description").doesNotExist());

        mockMvc.perform(get("/api/movies/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre").value("Thriller"));

        mockMvc.perform(delete("/api/admin/movies/{id}", id).with(bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/movies/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameTitleInAnotherLanguage_isAllowed_butExactDuplicateIsNot() throws Exception {
        movieRepository.save(movie("Jawan", "Hindi", "Action"));

        mockMvc.perform(post("/api/admin/movies").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Jawan", "language": "Tamil", "genre": "Action", "durationMinutes": 169}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/movies").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "JAWAN", "language": "hindi", "genre": "Action", "durationMinutes": 169}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MOVIE_ALREADY_EXISTS"));
    }

    @Test
    void invalidMovie_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/movies").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "language": "English", "genre": "Drama", "durationMinutes": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)));

        mockMvc.perform(post("/api/admin/movies").with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "X", "language": "English", "genre": "Drama", "durationMinutes": 90,
                                 "releaseDate": "not-a-date"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void publicSearch_filtersSortsAndPaginates() throws Exception {
        movieRepository.save(movie("Inception", "English", "Sci-Fi"));
        movieRepository.save(movie("Interstellar", "English", "Sci-Fi"));
        movieRepository.save(movie("Jawan", "Hindi", "Action"));
        movieRepository.save(movie("The Dark Knight", "English", "Action"));

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].title",
                        contains("Inception", "Interstellar", "Jawan", "The Dark Knight")));

        mockMvc.perform(get("/api/movies").param("language", "english").param("genre", "sci-fi"))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/movies").param("title", "INTER"))
                .andExpect(jsonPath("$.content[*].title", contains("Interstellar")));

        mockMvc.perform(get("/api/movies").param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/movies").param("sort", "title,desc"))
                .andExpect(jsonPath("$.content[0].title").value("The Dark Knight"));
    }

    @Test
    void sortingByUnknownField_returns400() throws Exception {
        mockMvc.perform(get("/api/movies").param("sort", "passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    private static Movie movie(String title, String language, String genre) {
        return new Movie(title, null, language, genre, 120, "UA", LocalDate.of(2020, 1, 1));
    }
}
