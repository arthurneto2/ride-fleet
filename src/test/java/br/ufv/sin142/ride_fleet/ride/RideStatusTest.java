package br.ufv.sin142.ride_fleet.ride;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da maquina de estados da corrida.
 *
 * Java puro: nao sobe contexto Spring e nao toca no banco, conforme exigido pela
 * tarefa de testes unitarios da Semana 1.
 */
class RideStatusTest {

    @DisplayName("Transicoes permitidas pela maquina de estados")
    @ParameterizedTest(name = "{0} -> {1} deve ser permitido")
    @CsvSource({
            // fluxo principal: request -> match -> confirm -> in_transit -> complete
            "REQUEST,    MATCH",
            "MATCH,      CONFIRM",
            "CONFIRM,    IN_TRANSIT",
            "IN_TRANSIT, COMPLETE",

            // delegacao de saida
            "REQUEST,    DELEGATED",

            // volta ao pool local
            "MATCH,      REQUEST",
            "DELEGATED,  REQUEST",

            // progresso reportado por um parceiro via Core
            "DELEGATED,  CONFIRM",
            "DELEGATED,  IN_TRANSIT",
            "DELEGATED,  COMPLETE",

            // cancelamento a partir de qualquer estado nao terminal
            "REQUEST,    CANCELLED",
            "MATCH,      CANCELLED",
            "CONFIRM,    CANCELLED",
            "IN_TRANSIT, CANCELLED",
            "DELEGATED,  CANCELLED"
    })
    void shouldAllowValidTransitions(RideStatus from, RideStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @DisplayName("Transicoes proibidas pela maquina de estados")
    @ParameterizedTest(name = "{0} -> {1} deve ser proibido")
    @CsvSource({
            // nao se pode pular etapas do fluxo
            "REQUEST,    CONFIRM",
            "REQUEST,    IN_TRANSIT",
            "REQUEST,    COMPLETE",
            "MATCH,      IN_TRANSIT",
            "MATCH,      COMPLETE",
            "CONFIRM,    COMPLETE",

            // nao se pode voltar no fluxo
            "CONFIRM,    MATCH",
            "CONFIRM,    REQUEST",
            "IN_TRANSIT, CONFIRM",
            "IN_TRANSIT, REQUEST",

            // corrida ja atribuida localmente nao pode ser delegada
            "MATCH,      DELEGATED",
            "CONFIRM,    DELEGATED",
            "IN_TRANSIT, DELEGATED",

            // estados terminais nao saem de onde estao
            "COMPLETE,   REQUEST",
            "COMPLETE,   IN_TRANSIT",
            "COMPLETE,   CANCELLED",
            "CANCELLED,  REQUEST",
            "CANCELLED,  MATCH",
            "CANCELLED,  COMPLETE"
    })
    void shouldRejectInvalidTransitions(RideStatus from, RideStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @DisplayName("Nenhum estado transiciona para si mesmo")
    @ParameterizedTest(name = "{0} -> {0} deve ser proibido")
    @EnumSource(RideStatus.class)
    void shouldRejectSelfTransition(RideStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    @DisplayName("COMPLETE e CANCELLED sao os unicos estados terminais")
    void shouldIdentifyTerminalStates() {
        assertThat(RideStatus.COMPLETE.isTerminal()).isTrue();
        assertThat(RideStatus.CANCELLED.isTerminal()).isTrue();

        assertThat(RideStatus.REQUEST.isTerminal()).isFalse();
        assertThat(RideStatus.MATCH.isTerminal()).isFalse();
        assertThat(RideStatus.CONFIRM.isTerminal()).isFalse();
        assertThat(RideStatus.IN_TRANSIT.isTerminal()).isFalse();
        assertThat(RideStatus.DELEGATED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("Estado terminal nao tem nenhuma transicao de saida")
    void terminalStatesShouldHaveNoOutgoingTransitions() {
        for (RideStatus terminal : new RideStatus[] { RideStatus.COMPLETE, RideStatus.CANCELLED }) {
            assertThat(terminal.allowedTransitions()).isEmpty();
        }
    }

    @Test
    @DisplayName("A corrida delegada pode voltar ao pool local quando a Saga compensa")
    void delegatedRideShouldBeAbleToReturnToLocalPool() {
        assertThat(RideStatus.DELEGATED.canTransitionTo(RideStatus.REQUEST)).isTrue();
    }

    @Test
    @DisplayName("O conjunto de transicoes permitidas e imutavel")
    void allowedTransitionsShouldBeImmutable() {
        assertThat(RideStatus.REQUEST.allowedTransitions())
                .containsExactlyInAnyOrder(RideStatus.MATCH, RideStatus.DELEGATED, RideStatus.CANCELLED);

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> RideStatus.REQUEST.allowedTransitions().add(RideStatus.COMPLETE));
    }
}
