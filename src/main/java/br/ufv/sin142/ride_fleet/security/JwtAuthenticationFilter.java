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
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String DELEGATION_PATH_PREFIX = "/api/v1/delegation";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final String coreApiKey;

    /**
     * Dependencias pelo construtor, e nao @Value em campo, para permitir teste
     * unitario do filtro sem contexto Spring.
     */
    public JwtAuthenticationFilter(
            JwtService jwtService,
            @Value("${security.core.api-key:core-shared-secret-key-123}") String coreApiKey) {
        this.jwtService = jwtService;
        this.coreApiKey = coreApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        // 1. Chamadas do Core, identificadas pela chave compartilhada.
        //
        // A chave e avaliada em qualquer rota, nao so em /delegation, porque a
        // Semana 3 precisa consultar disponibilidade (GET /api/v1/drivers) antes
        // de responder a um leilao. A autorizacao por rota fica no SecurityConfig,
        // que restringe o Core a leitura fora de /delegation.
        if (apiKeyHeader != null) {
            if (coreApiKey.equals(apiKeyHeader)) {
                authenticateCore();
                filterChain.doFilter(request, response);
            } else {
                unauthorized(response, "Chave X-API-Key invalida ou ausente.");
            }
            return;
        }

        // 2. Rota de delegacao sem chave nenhuma: barra aqui.
        if (path.startsWith(DELEGATION_PATH_PREFIX)) {
            unauthorized(response, "Chave X-API-Key invalida ou ausente.");
            return;
        }

        // 3. Autenticacao via JWT para passageiros, motoristas e administradores.
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                DecodedJWT decodedJWT = jwtService.validateToken(token);
                AuthenticatedUser user = toAuthenticatedUser(decodedJWT);

                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(user.role());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user, null, Collections.singletonList(authority)));
            } catch (JWTVerificationException e) {
                unauthorized(response, "Token JWT invalido ou expirado.");
                return;
            } catch (IllegalArgumentException e) {
                // subject que nao e UUID: falha fechada, nunca 500
                unauthorized(response, "Token JWT invalido ou expirado.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private AuthenticatedUser toAuthenticatedUser(DecodedJWT decodedJWT) {
        String subject = jwtService.getSubject(decodedJWT);
        String role = jwtService.getRole(decodedJWT);
        String email = jwtService.getEmail(decodedJWT);

        if (subject == null || role == null) {
            throw new IllegalArgumentException("Token sem subject ou role.");
        }
        return new AuthenticatedUser(UUID.fromString(subject), email, role);
    }

    /**
     * O Core nao e um usuario: o principal fica sendo a String "CORE", de modo que
     * pedir um AuthenticatedUser numa rota do Core falha explicitamente em vez de
     * produzir um registro com id nulo.
     */
    private void authenticateCore() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "CORE", null,
                        Collections.singletonList(new SimpleGrantedAuthority(JwtService.ROLE_CORE))));
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + message + "\"}");
    }
}
