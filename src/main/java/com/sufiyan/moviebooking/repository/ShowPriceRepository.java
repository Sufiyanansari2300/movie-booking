package com.sufiyan.moviebooking.repository;

import com.sufiyan.moviebooking.entity.ShowPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShowPriceRepository extends JpaRepository<ShowPrice, Long> {

    List<ShowPrice> findByShowId(Long showId);

    List<ShowPrice> findByShowIdIn(Collection<Long> showIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ShowPrice p where p.show.id = :showId")
    int deleteByShowId(@Param("showId") Long showId);
}
