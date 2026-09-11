package br.ufv.sin142.ride_fleet.shared.exception;

import br.ufv.sin142.ride_fleet.ride.RideStatus;

/**
 * Lancada quando se tenta mover uma corrida para um estado que a maquina de
 * estados nao permite a partir do estado atual.
 *
 * Guarda os dois estados porque o log estruturado da Semana 2 exige os campos
 * {@code estado_anterior} e {@code estado_novo}.
 */
public class InvalidRideTransitionException extends RuntimeException {

    private final RideStatus from;
    private final RideStatus to;

    public InvalidRideTransitionException(RideStatus from, RideStatus to) {
        super("Transicao de corrida invalida: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public RideStatus getFrom() {
        return from;
    }

    public RideStatus getTo() {
        return to;
    }
}
