package com.aykhedma.repository;

import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "UPDATE users SET password = :password WHERE email = :email", nativeQuery = true)
    void updatePassword(@org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("password") String password);

    @Modifying
    @Query(value = "DELETE FROM consumer_saved_providers WHERE consumer_id = :userId OR provider_id = :userId", nativeQuery = true)
    int deleteSavedProviderLinks(@Param("userId") Long userId);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    List<User> findByRole(UserType role);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.enabled = true")
    Optional<User> findActiveUserByEmail(@Param("email") String email);

    @Query(value = "SELECT u.id, u.name, u.email, u.phone_number AS phoneNumber, " +
            "u.role, u.profile_image AS profileImage, u.preferred_language AS preferredLanguage, " +
            "u.created_at AS createdAt, u.enabled " +
            "FROM users u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:status IS NULL OR u.enabled = :status) AND " +
            "(CAST(:startDate AS timestamp) IS NULL OR u.created_at >= :startDate) AND " +
            "(CAST(:endDate AS timestamp) IS NULL OR u.created_at <= :endDate) AND " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.phone_number) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            countQuery = "SELECT COUNT(*) FROM users u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:status IS NULL OR u.enabled = :status) AND " +
            "(CAST(:startDate AS timestamp) IS NULL OR u.created_at >= :startDate) AND " +
            "(CAST(:endDate AS timestamp) IS NULL OR u.created_at <= :endDate) AND " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.phone_number) LIKE LOWER(CONCAT('%', :keyword, '%')))",
            nativeQuery = true)
    Page<Object[]> searchUsersNative(
            @Param("role") String role,
            @Param("status") Boolean status,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            @Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);
}