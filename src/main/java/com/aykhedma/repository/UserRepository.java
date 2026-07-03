package com.aykhedma.repository;

import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

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

}