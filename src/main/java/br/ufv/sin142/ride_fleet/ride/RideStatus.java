package br.ufv.sin142.ride_fleet.ride;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados possiveis de uma corrida e a maquina de estados que governa as transicoes.
 *
 * A tabela de transicoes vive aqui, e nao espalhada pelos services, por dois motivos:
 * e a unica fonte de verdade da regra, e e testavel sem contexto Spring e sem banco.
 *
 * Fluxo principal: REQUEST -> MATCH -> CONFIRM -> IN_TRANSIT -> COMPLETE
 */
public enum RideStatus {

    /** Corrida solicitada, aguardando atribuicao de motorista no pool local. */
    REQUEST,

    /** Motorista atribuido pelo sistema, aguardando aceite dele. */
    MATCH,

    /** Motorista aceitou a corrida e esta a caminho do passageiro. */
    CONFIRM,

    /** Passageiro embarcado, corrida em andamento. */
    IN_TRANSIT,

    /** Corrida concluida. Estado terminal. */
    COMPLETE,

    /** Corrida assumida por um servico parceiro via Core. */
    DELEGATED,

    /** Corrida cancelada. Estado terminal. */
    CANCELLED;

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<RideStatus, Set<RideStatus>> transitions = new EnumMap<>(RideStatus.class);

        transitions.put(REQUEST, EnumSet.of(
                MATCH,       // motorista local disponivel foi atribuido
                DELEGATED,   // overflow: parceiro assumiu a corrida via Core
                CANCELLED));

        transitions.put(MATCH, EnumSet.of(
                CONFIRM,     // motorista aceitou
                REQUEST,     // motorista recusou ou expirou: volta ao pool local
                CANCELLED));

        transitions.put(CONFIRM, EnumSet.of(
                IN_TRANSIT,  // passageiro embarcou
                CANCELLED));

        transitions.put(IN_TRANSIT, EnumSet.of(
                COMPLETE,
                CANCELLED)); // cancelamento excepcional durante a viagem

        transitions.put(DELEGATED, EnumSet.of(
                CONFIRM,     // parceiro reportou aceite via Core
                IN_TRANSIT,  // parceiro reportou embarque via Core
                COMPLETE,    // parceiro reportou conclusao via Core
                REQUEST,     // compensacao da Saga: corrida volta ao pool local
                CANCELLED));

        transitions.put(COMPLETE, EnumSet.noneOf(RideStatus.class));
        transitions.put(CANCELLED, EnumSet.noneOf(RideStatus.class));

        transitions.replaceAll((status, targets) -> Collections.unmodifiableSet(targets));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    /** Estados alcancaveis a partir deste. Vazio para estados terminais. */
    public Set<RideStatus> allowedTransitions() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    /** {@code true} se a corrida pode sair deste estado para {@code target}. */
    public boolean canTransitionTo(RideStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    /** {@code true} se a corrida nao pode mais mudar de estado. */
    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
