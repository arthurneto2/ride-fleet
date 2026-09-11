package br.ufv.sin142.ride_fleet.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do filtro de autenticacao.
 *
 * O foco e o que o filtro coloca no SecurityContext: hoje ele le o UUID do
 * token e descarta, autenticando pelo e-mail. Os endpoints novos precisam do
 * UUID, e busca-lo por e-mail a cada requisicao custaria um round trip de banco
 * para recuperar informacao que o proprio token ja carrega - poluindo a janela
 * de latencia que decide o overflow.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "segredo-de-teste-com-tamanho-suficiente-123456";
    private static final String CORE_API_KEY = "chave-core-de-teste";

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
        filter = new JwtAuthenticationFilter(jwtService, CORE_API_KEY);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    @DisplayName("Token valido de passageiro autentica com o UUID do token")
    void shouldAuthenticatePassengerWithUuidFromToken() throws Exception {
        UUID passengerId = UUID.randomUUID();
        String token = jwtService.generatePassengerToken(passengerId, "joao@exemplo.com");

        MockHttpServletRequest request = requestTo("/api/v1/rides/request");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(user.id()).isEqualTo(passengerId);
        assertThat(user.email()).isEqualTo("joao@exemplo.com");
        assertThat(user.role()).isEqualTo("ROLE_PASSENGER");

        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_PASSENGER");
    }

    @Test
    @DisplayName("Token valido de motorista autentica com papel de motorista")
    void shouldAuthenticateDriver() throws Exception {
        UUID driverId = UUID.randomUUID();
        String token = jwtService.generateDriverToken(driverId, "maria@exemplo.com");

        MockHttpServletRequest request = requestTo("/api/v1/drivers/me");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, chain);

        AuthenticatedUser user =
                (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(user.id()).isEqualTo(driverId);
        assertThat(user.role()).isEqualTo("ROLE_DRIVER");
    }

    @Test
    @DisplayName("Token assinado com outro segredo devolve 401")
    void shouldRejectTokenSignedWithAnotherSecret() throws Exception {
        String forged = JWT.create()
                .withSubject(UUID.randomUUID().toString())
                .withClaim("email", "atacante@exemplo.com")
                .withClaim("role", "ROLE_PASSENGER")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256("outro-segredo-completamente-diferente"));

        MockHttpServletRequest request = requestTo("/api/v1/rides/request");
        request.addHeader("Authorization", "Bearer " + forged);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token com subject que nao e UUID devolve 401, nao 500")
    void shouldRejectTokenWhoseSubjectIsNotAUuid() throws Exception {
        String token = JWT.create()
                .withSubject("nao-sou-um-uuid")
                .withClaim("email", "x@exemplo.com")
                .withClaim("role", "ROLE_PASSENGER")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256(SECRET));

        MockHttpServletRequest request = requestTo("/api/v1/rides/request");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, chain);

        // falha fechada: token forjado com subject estranho nao pode virar 500
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Requisicao sem cabecalho segue a cadeia sem autenticar")
    void shouldPassThroughWhenNoHeaderPresent() throws Exception {
        filter.doFilter(requestTo("/api/v1/rides/request"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("X-API-Key valida concede ROLE_CORE em rota de delegacao")
    void shouldGrantCoreRoleOnDelegationWithValidApiKey() throws Exception {
        MockHttpServletRequest request = requestTo("/api/v1/delegation/bid");
        request.addHeader("X-API-Key", CORE_API_KEY);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CORE");
    }

    @Test
    @DisplayName("Rota de delegacao sem X-API-Key devolve 401")
    void shouldRejectDelegationWithoutApiKey() throws Exception {
        filter.doFilter(requestTo("/api/v1/delegation/bid"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Rota de delegacao com X-API-Key errada devolve 401")
    void shouldRejectDelegationWithWrongApiKey() throws Exception {
        MockHttpServletRequest request = requestTo("/api/v1/delegation/bid");
        request.addHeader("X-API-Key", "chave-errada");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("X-API-Key valida concede ROLE_CORE tambem fora de /delegation")
    void shouldGrantCoreRoleOutsideDelegationWithValidApiKey() throws Exception {
        // a Semana 3 precisa consultar GET /api/v1/drivers?status=AVAILABLE
        // para avaliar disponibilidade antes de responder a um leilao
        MockHttpServletRequest request = requestTo("/api/v1/drivers");
        request.addHeader("X-API-Key", CORE_API_KEY);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CORE");
    }
}
