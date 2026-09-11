package br.ufv.sin142.ride_fleet.shared.exception;

import br.ufv.sin142.ride_fleet.shared.dto.ApiErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento global de erros da API.
 *
 * Duas restricoes de desenho que NAO podem ser violadas:
 *
 * 1. Nao existe handler para Exception, Throwable ou RuntimeException. O 404 de
 *    rota inexistente chega aqui como NoResourceFoundException, que e uma
 *    RuntimeException; um catch-all o converteria em 500 e quebraria o teste
 *    shouldAllowCoreEndpointsWithValidApiKey, que espera 404 em
 *    /api/v1/delegation/bid enquanto a controller nao existe.
 *
 * 2. Esta classe NAO herda de ResponseEntityExceptionHandler. Herdar troca o
 *    corpo por ProblemDetail (RFC 7807), que nao tem a chave message exigida
 *    pelos testes de auth ja existentes.
 *
 * Sem catch-all, excecoes nao mapeadas caem no BasicErrorController do Boot,
 * que ja devolve JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorDTO> handleResourceNotFound(ResourceNotFoundException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRideTransitionException.class)
    public ResponseEntity<ApiErrorDTO> handleInvalidRideTransition(InvalidRideTransitionException ex,
                                                                  HttpServletRequest request) {
        log.warn("Transicao de corrida recusada: {} -> {}", ex.getFrom(), ex.getTo());
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorDTO> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    /** Cobre tambem IdentityMismatchException, que estende esta. */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiErrorDTO> handleForbiddenOperation(ForbiddenOperationException ex,
                                                               HttpServletRequest request) {
        log.warn("Acesso negado em {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorDTO> handleInvalidCredentials(InvalidCredentialsException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request);
    }

    /**
     * Mantem em 400 o comportamento atual de e-mail/placa ja cadastrados, que e
     * o codigo documentado em api_spec.md secao 3.1.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDTO> handleIllegalArgument(IllegalArgumentException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDTO> handleValidation(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorDTO.of("Bad Request", "Dados invalidos na requisicao.",
                        request.getRequestURI(), fieldErrors));
    }

    /**
     * Id malformado no caminho, como GET /api/v1/rides/abc onde se espera UUID.
     * Sem este mapeamento a resposta seria 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request",
                "Valor invalido para o parametro '" + ex.getName() + "'.", request);
    }

    /** Rede de seguranca para unicidade que escapou da checagem em codigo. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDTO> handleDataIntegrity(DataIntegrityViolationException ex,
                                                           HttpServletRequest request) {
        log.warn("Violacao de integridade em {}", request.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, "Conflict",
                "Operacao viola uma restricao de integridade dos dados.", request);
    }

    /** Perde a corrida por escrita concorrente na mesma linha (@Version). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorDTO> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                            HttpServletRequest request) {
        log.warn("Conflito de escrita concorrente em {}", request.getRequestURI());
        return build(HttpStatus.CONFLICT, "Conflict",
                "O recurso foi alterado por outra operacao. Tente novamente.", request);
    }

    private ResponseEntity<ApiErrorDTO> build(HttpStatus status, String error, String message,
                                              HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiErrorDTO.of(error, message, request.getRequestURI()));
    }
}
