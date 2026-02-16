package com.aykhedma.repository;

import com.aykhedma.model.user.Consumer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumerRepository extends JpaRepository<Consumer, Long> {

    Optional<Consumer> findByEmail(String email);

    Optional<Consumer> findByPhoneNumber(String phoneNumber);

    @Query("SELECT c FROM Consumer c WHERE c.averageRating >= :minRating")
    List<Consumer> findTopRatedConsumers(@Param("minRating") Double minRating);
}