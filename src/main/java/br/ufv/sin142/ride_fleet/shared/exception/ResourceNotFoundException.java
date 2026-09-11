package br.ufv.sin142.ride_fleet.shared.exception;

/** Recurso inexistente. Mapeada para 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " nao encontrado(a): " + id);
    }
}
