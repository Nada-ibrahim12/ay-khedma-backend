package com.aykhedma.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // for browsers that don't support WebSocket

        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("*"); // for native apps
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // from server to client
        registry.enableSimpleBroker("/topic", "/queue", "/user");

        // from client to server
        registry.setApplicationDestinationPrefixes("/app");

        // for user-specific messages
        registry.setUserDestinationPrefix("/user");
    }
}