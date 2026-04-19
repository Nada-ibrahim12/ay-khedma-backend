package com.aykhedma.repository;

import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    List<User> findByRole(UserType role);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.enabled = true")
    Optional<User> findActiveUserByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:status IS NULL OR u.enabled = :status) AND " +
            "(CAST(:startDate AS timestamp) IS NULL OR u.createdAt >= :startDate) AND " +
            "(CAST(:endDate AS timestamp) IS NULL OR u.createdAt <= :endDate)")
    org.springframework.data.domain.Page<User> searchUsers(
            @Param("role") UserType role,
            @Param("status") Boolean status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            org.springframework.data.domain.Pageable pageable);
}