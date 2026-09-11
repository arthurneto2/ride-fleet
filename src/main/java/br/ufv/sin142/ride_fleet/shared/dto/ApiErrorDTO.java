package br.ufv.sin142.ride_fleet.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Corpo padrao de erro da API.
 *
 * As chaves error e message sao obrigatorias por compatibilidade: a camada de
 * auth ja responde nesse formato e ha testes afirmando $.message. Trocar por
 * ProblemDetail (RFC 7807) quebraria esses testes e o contrato publicado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDTO(
        String error,
        String message,
        Instant timestamp,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiErrorDTO of(String error, String message, String path) {
        return new ApiErrorDTO(error, message, Instant.now(), path, null);
    }

    public static ApiErrorDTO of(String error, String message, String path, Map<String, String> fieldErrors) {
        return new ApiErrorDTO(error, message, Instant.now(), path, fieldErrors);
    }
}
