package br.ufv.sin142.ride_fleet.shared.exception;

/** Usuario autenticado, mas sem permissao sobre este recurso. Mapeada para 403. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
