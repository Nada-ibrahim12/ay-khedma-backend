package com.aykhedma.repository;

import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private Consumer savedConsumer;

    @BeforeEach
    void setUp() {
        Consumer consumer = Consumer.builder()
                .name("Ahmed Test")
                .email("ahmed@test.com")
                .phoneNumber("01012345678")
                .password("encodedPassword1")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();
        savedConsumer = entityManager.persistAndFlush(consumer);
    }

    // ═══════════════════════════════════════════════════════
    // findByEmail
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("findByEmail() should return user when email exists")
    void findByEmail_existingEmail_returnsUser() {
        Optional<User> result = userRepository.findByEmail("ahmed@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Ahmed Test");
    }

    @Test
    @DisplayName("findByEmail() should return empty when email does not exist")
    void findByEmail_nonExistentEmail_returnsEmpty() {
        Optional<User> result = userRepository.findByEmail("nobody@test.com");

        assertThat(result).isEmpty();
    }

    // ═══════════════════════════════════════════════════════
    // findByPhoneNumber
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("findByPhoneNumber() should return user when phone exists")
    void findByPhoneNumber_existingPhone_returnsUser() {
        Optional<User> result = userRepository.findByPhoneNumber("01012345678");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("ahmed@test.com");
    }

    @Test
    @DisplayName("findByPhoneNumber() should return empty when phone does not exist")
    void findByPhoneNumber_nonExistentPhone_returnsEmpty() {
        Optional<User> result = userRepository.findByPhoneNumber("01099999999");

        assertThat(result).isEmpty();
    }

    // ═══════════════════════════════════════════════════════
    // existsByEmail / existsByPhoneNumber
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("existsByEmail() should return true for existing email")
    void existsByEmail_exists_returnsTrue() {
        assertThat(userRepository.existsByEmail("ahmed@test.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail() should return false for non-existent email")
    void existsByEmail_notExists_returnsFalse() {
        assertThat(userRepository.existsByEmail("ghost@test.com")).isFalse();
    }

    @Test
    @DisplayName("existsByPhoneNumber() should return true for existing phone")
    void existsByPhoneNumber_exists_returnsTrue() {
        assertThat(userRepository.existsByPhoneNumber("01012345678")).isTrue();
    }

    @Test
    @DisplayName("existsByPhoneNumber() should return false for non-existent phone")
    void existsByPhoneNumber_notExists_returnsFalse() {
        assertThat(userRepository.existsByPhoneNumber("01099999999")).isFalse();
    }

    // ═══════════════════════════════════════════════════════
    // findByRole
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("findByRole() should return users with matching role")
    void findByRole_consumer_returnsConsumers() {
        List<User> consumers = userRepository.findByRole(UserType.CONSUMER);

        assertThat(consumers).isNotEmpty();
        assertThat(consumers).allSatisfy(u -> assertThat(u.getRole()).isEqualTo(UserType.CONSUMER));
    }

    @Test
    @DisplayName("findByRole() should return empty for role with no users")
    void findByRole_admin_returnsEmpty() {
        List<User> admins = userRepository.findByRole(UserType.ADMIN);

        assertThat(admins).isEmpty();
    }

    // ═══════════════════════════════════════════════════════
    // findActiveUserByEmail (custom @Query)
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("findActiveUserByEmail() should return user when enabled=true")
    void findActiveUserByEmail_enabledUser_returnsUser() {
        Optional<User> result = userRepository.findActiveUserByEmail("ahmed@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("findActiveUserByEmail() should return empty when user is disabled")
    void findActiveUserByEmail_disabledUser_returnsEmpty() {
        Consumer disabled = Consumer.builder()
                .name("Disabled User")
                .email("disabled@test.com")
                .phoneNumber("01098765432")
                .password("encodedPassword2")
                .role(UserType.CONSUMER)
                .enabled(false)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();
        entityManager.persistAndFlush(disabled);

        Optional<User> result = userRepository.findActiveUserByEmail("disabled@test.com");

        assertThat(result).isEmpty();
    }
}
