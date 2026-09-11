package br.ufv.sin142.ride_fleet.config;

import br.ufv.sin142.ride_fleet.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Acesso nao autorizado. Faca login primeiro.\"}");
                })
                // sem este handler, um 403 sai com corpo vazio, diferente de todos
                // os outros erros da API
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Forbidden\", \"message\": \"Sem permissao para acessar este recurso.\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                .requestMatchers("/api/v1/delegation/**").hasRole("CORE")

                // ATENCAO A ORDEM: as rotas literais /me precisam vir ANTES das
                // regras de /api/v1/drivers/** restritas a ADMIN. Invertido, o
                // proprio motorista recebe 403 ao atualizar seu status.
                .requestMatchers("/api/v1/drivers/me", "/api/v1/drivers/me/**").hasRole("DRIVER")

                .requestMatchers(HttpMethod.GET, "/api/v1/drivers", "/api/v1/drivers/*")
                    .hasAnyRole("ADMIN", "CORE")
                .requestMatchers(HttpMethod.POST, "/api/v1/drivers").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/drivers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/drivers/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/v1/rides/request").hasRole("PASSENGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/rides/*/cancel")
                    .hasAnyRole("PASSENGER", "DRIVER")
                .requestMatchers(HttpMethod.GET, "/api/v1/rides/pending")
                    .hasAnyRole("DRIVER", "ADMIN", "CORE")
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/rides/*/accept", "/api/v1/rides/*/decline",
                        "/api/v1/rides/*/start", "/api/v1/rides/*/complete").hasRole("DRIVER")

                // GET /api/v1/rides/{id} fica apenas autenticado de proposito: a
                // regra "so o dono ou o motorista atribuido" nao e expressavel por
                // padrao de rota e vive no RideService.
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
