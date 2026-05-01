package com.aykhedma.config;

import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import java.security.Principal;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            log.debug("WebSocketAuthInterceptor: accessor is null for message headers={}", message.getHeaders());
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.debug("WebSocketAuthInterceptor: CONNECT received. messageHeaders={}", message.getHeaders());

            String userIdHeader = accessor.getFirstNativeHeader("X-User-Id");
            if (userIdHeader != null) {
                try {
                    Long userId = Long.parseLong(userIdHeader);
                    accessor.setUser(principalForUserId(userId));
                    log.info("WebSocketAuthInterceptor: set Principal from X-User-Id={}", userId);
                    return message;
                } catch (NumberFormatException ex) {
                    log.warn("WebSocketAuthInterceptor: invalid X-User-Id header='{}'", userIdHeader);
                }
            }

            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                String email = extractEmailFromToken(token);
                log.debug("WebSocketAuthInterceptor: token present, extracted email={}", email);
                if (email != null) {
                    userRepository.findByEmail(email)
                            .map(user -> (Principal) () -> user.getId().toString())
                            .ifPresent(userPrincipal -> {
                                accessor.setUser(userPrincipal);
                                log.info("WebSocketAuthInterceptor: set Principal from JWT email={} -> userId={}",
                                        email, userPrincipal.getName());
                            });
                }
            } else {
                log.debug("WebSocketAuthInterceptor: no Authorization Bearer header present on CONNECT");
            }
        }
        return message;
    }

    private String extractEmailFromToken(String token) {
        try {
            if (!jwtService.isTokenValid(token)) {
                return null;
            }
            return jwtService.extractUsername(token);
        } catch (Exception ex) {
            return null;
        }
    }

    private Principal principalForUserId(Long userId) {
        return () -> userId.toString();
    }
}