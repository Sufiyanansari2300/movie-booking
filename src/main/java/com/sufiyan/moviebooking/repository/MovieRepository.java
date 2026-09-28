package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    boolean existsByTitleIgnoreCaseAndLanguageIgnoreCase(String title, String language);

    boolean existsByTitleIgnoreCaseAndLanguageIgnoreCaseAndIdNot(String title, String language, Long id);

    /** All filters are optional (null = ignore). Title matches as a case-insensitive substring. */
    @Query("""
            select m from Movie m
            where (:title is null or lower(m.title) like lower(concat('%', :title, '%')))
              and (:language is null or lower(m.language) = lower(:language))
              and (:genre is null or lower(m.genre) = lower(:genre))
            """)
    Page<Movie> search(@Param("title") String title, @Param("language") String language,
                       @Param("genre") String genre, Pageable pageable);
}
