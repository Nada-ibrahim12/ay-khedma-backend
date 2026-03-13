package com.aykhedma.config;

// import io.jsonwebtoken.Claims;
// import io.jsonwebtoken.JwtException;
// import io.jsonwebtoken.Jwts;
// import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.security.Principal;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                // Validate token and extract user ID
                Long userId = extractUserIdFromToken(token);
                if (userId != null) {
                    accessor.setUser(new Principal() {
                        @Override
                        public String getName() {
                            return userId.toString();
                        }
                    });
                }
            }
        }
        return message;
    }

    private Long extractUserIdFromToken(String token) {
    //     try {
    //         // Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            
    //         Claims claims = Jwts.parserBuilder()
    //                 .setSigningKey(key)
    //                 .build()
    //                 .parseClaimsJws(token)
    //                 .getBody();
            
    //         // Extract userId from claims
    //         Object userIdObj = claims.get("userId");
    //         if (userIdObj instanceof Number) {
    //             return ((Number) userIdObj).longValue();
    //         } else if (userIdObj instanceof String) {
    //             return Long.parseLong((String) userIdObj);
    //         }
            
            return null;
    //     } catch (JwtException | IllegalArgumentException e) {
    //         // Log the error if needed
    //         System.err.println("JWT Token validation failed: " + e.getMessage());
    //         return null;
    //     }
    }
}