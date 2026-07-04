package br.ufv.sin142.ride_fleet.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Value("${security.core.api-key:core-shared-secret-key-123}")
    private String coreApiKey;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();

        // 1. Autenticação das chamadas do Core (Integração de Delegação/2PC)
        if (path.startsWith("/api/v1/delegation")) {
            String apiKeyHeader = request.getHeader("X-API-Key");
            if (coreApiKey.equals(apiKeyHeader)) {
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_CORE");
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken("CORE", null, Collections.singletonList(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                filterChain.doFilter(request, response);
                return;
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Chave X-API-Key invalida ou ausente.\"}");
                return;
            }
        }

        // 2. Autenticação via JWT para Passageiros e Motoristas
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                DecodedJWT decodedJWT = jwtService.validateToken(token);
                String userId = jwtService.getSubject(decodedJWT);
                String role = jwtService.getRole(decodedJWT);
                String email = jwtService.getEmail(decodedJWT);

                if (userId != null && role != null) {
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(email, null, Collections.singletonList(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JWTVerificationException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Token JWT invalido ou expirado.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
