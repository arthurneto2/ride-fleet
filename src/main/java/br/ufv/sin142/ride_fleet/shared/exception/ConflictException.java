package br.ufv.sin142.ride_fleet.shared.exception;

/** Regra de negocio violada pelo estado atual do recurso. Mapeada para 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
