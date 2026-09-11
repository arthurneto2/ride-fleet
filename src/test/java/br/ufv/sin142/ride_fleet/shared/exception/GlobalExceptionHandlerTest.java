package br.ufv.sin142.ride_fleet.shared.exception;

import br.ufv.sin142.ride_fleet.ride.RideStatus;
import br.ufv.sin142.ride_fleet.shared.dto.ApiErrorDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do tratamento global de erros.
 *
 * Unitarios de proposito: o handler e uma classe comum, entao da para chamar os
 * metodos direto e verificar status e corpo sem subir contexto Spring.
 *
 * O contrato de corpo {error, message} e obrigatorio: dois testes de auth ja
 * existentes afirmam $.message, e mudar para ProblemDetail (RFC 7807) os
 * quebraria.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides/x");

    @Test
    @DisplayName("Recurso inexistente devolve 404")
    void shouldMapResourceNotFoundTo404() {
        ResponseEntity<ApiErrorDTO> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Corrida", "abc"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).contains("Corrida").contains("abc");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/rides/x");
    }

    @Test
    @DisplayName("Transicao de estado invalida devolve 409")
    void shouldMapInvalidRideTransitionTo409() {
        ResponseEntity<ApiErrorDTO> response = handler.handleInvalidRideTransition(
                new InvalidRideTransitionException(RideStatus.REQUEST, RideStatus.COMPLETE), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message()).contains("REQUEST").contains("COMPLETE");
    }

    @Test
    @DisplayName("Violacao de regra de negocio devolve 409")
    void shouldMapConflictTo409() {
        ResponseEntity<ApiErrorDTO> response =
                handler.handleConflict(new ConflictException("Motorista possui corrida em andamento"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Motorista possui corrida em andamento");
    }

    @Test
    @DisplayName("Acesso a recurso de outro usuario devolve 403")
    void shouldMapForbiddenOperationTo403() {
        ResponseEntity<ApiErrorDTO> response = handler.handleForbiddenOperation(
                new ForbiddenOperationException("Corrida nao pertence ao usuario autenticado"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error()).isEqualTo("Forbidden");
    }

    @Test
    @DisplayName("Identidade do corpo divergindo do token devolve 403")
    void shouldMapIdentityMismatchTo403() {
        ResponseEntity<ApiErrorDTO> response = handler.handleForbiddenOperation(
                new IdentityMismatchException(UUID.randomUUID(), UUID.randomUUID()), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Argumento ilegal devolve 400, mantendo o comportamento atual do cadastro")
    void shouldMapIllegalArgumentTo400() {
        ResponseEntity<ApiErrorDTO> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Email ja cadastrado para outro passageiro."), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("Bad Request");
        assertThat(response.getBody().message()).contains("Email ja cadastrado");
    }

    @Test
    @DisplayName("Credenciais invalidas devolvem 401 com a mensagem exata esperada pelos testes de auth")
    void shouldMapInvalidCredentialsTo401() {
        ResponseEntity<ApiErrorDTO> response =
                handler.handleInvalidCredentials(new InvalidCredentialsException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().error()).isEqualTo("Unauthorized");
        assertThat(response.getBody().message()).isEqualTo("Credenciais invalidas.");
    }

    @Test
    @DisplayName("O handler nao captura Exception nem Throwable")
    void shouldNotDeclareACatchAllHandler() {
        // Um catch-all converteria o NoResourceFoundException de rota inexistente
        // em 500 e quebraria shouldAllowCoreEndpointsWithValidApiKey, que espera 404.
        boolean hasCatchAll = java.util.Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .flatMap(method -> {
                    var annotation = method.getAnnotation(
                            org.springframework.web.bind.annotation.ExceptionHandler.class);
                    return annotation == null
                            ? java.util.stream.Stream.<Class<?>>empty()
                            : java.util.Arrays.stream(annotation.value());
                })
                .anyMatch(type -> type == Exception.class || type == Throwable.class
                        || type == RuntimeException.class);

        assertThat(hasCatchAll).isFalse();
    }

    @Test
    @DisplayName("O handler nao herda de ResponseEntityExceptionHandler")
    void shouldNotExtendResponseEntityExceptionHandler() {
        // Herdar dele troca o corpo por ProblemDetail, que nao tem a chave message
        // exigida pelos testes de auth ja existentes.
        assertThat(GlobalExceptionHandler.class.getSuperclass()).isEqualTo(Object.class);
    }
}
