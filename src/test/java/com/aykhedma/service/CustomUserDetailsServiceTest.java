package com.aykhedma.service;

import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.security.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService Unit Tests")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("loadUserByUsername() should return UserDetails with correct authorities")
    void loadByUsername_existingUser_returnsCorrectDetails() {
        User user = Consumer.builder()
                .id(1L)
                .name("Test")
                .email("test@mail.com")
                .phoneNumber("01012345678")
                .password("encoded")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();

        when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("test@mail.com");

        assertThat(details).isInstanceOf(CustomUserDetails.class);
        assertThat(details.getUsername()).isEqualTo("test@mail.com");
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CONSUMER");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("loadUserByUsername() should throw when user not found")
    void loadByUsername_nonExistentUser_throws() {
        when(userRepository.findByEmail("ghost@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost@mail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
