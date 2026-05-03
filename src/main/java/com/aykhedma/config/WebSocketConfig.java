package com.aykhedma.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${websocket.endpoint:/ws-notifications}")
    private String websocketEndpoint;

    @Value("${websocket.allowed-origins:*}")
    private String websocketAllowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = websocketAllowedOrigins.split(",");

        registry.addEndpoint(websocketEndpoint)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS(); // for browsers that don't support WebSocket

        registry.addEndpoint(websocketEndpoint)
                .setAllowedOriginPatterns(allowedOrigins); // for native apps

        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // from server to client
        registry.enableSimpleBroker("/topic", "/queue");

        // from client to server
        registry.setApplicationDestinationPrefixes("/app");

        // for user-specific messages
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}