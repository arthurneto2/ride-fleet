package br.ufv.sin142.ride_fleet.shared.exception;

/**
 * Credenciais de login invalidas. Mapeada para 401.
 *
 * A mensagem e deliberadamente identica para senha errada, e-mail inexistente e
 * conta desativada: revelar qual dos tres ocorreu entrega informacao a quem
 * esta tentando descobrir contas validas.
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "Credenciais invalidas.";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
