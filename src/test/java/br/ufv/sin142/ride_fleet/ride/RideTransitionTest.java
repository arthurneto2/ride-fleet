package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.shared.exception.InvalidRideTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes do metodo de dominio que aplica a transicao na entidade.
 *
 * Enquanto RideStatusTest cobre a tabela de transicoes, aqui o foco e o efeito
 * colateral: estampar o relogio logico e recusar transicoes invalidas sem
 * deixar a entidade num estado intermediario.
 */
class RideTransitionTest {

    private Ride rideInStatus(RideStatus status) {
        return Ride.builder()
                .status(status)
                .originLatitude(-19.2012)
                .originLongitude(-46.2231)
                .originAddress("Predio de Aulas - UFV")
                .destinationLatitude(-19.2045)
                .destinationLongitude(-46.2290)
                .destinationAddress("Restaurante Universitario - UFV")
                .price(new BigDecimal("18.50"))
                .etaSeconds(360)
                .logicalTimestamp(7L)
                .build();
    }

    @Test
    @DisplayName("Transicao valida muda o estado e estampa o relogio logico")
    void shouldApplyValidTransitionAndStampLogicalTimestamp() {
        Ride ride = rideInStatus(RideStatus.CONFIRM);

        ride.transitionTo(RideStatus.IN_TRANSIT, 42L);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.IN_TRANSIT);
        assertThat(ride.getLogicalTimestamp()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Transicao invalida lanca InvalidRideTransitionException")
    void shouldRejectInvalidTransition() {
        Ride ride = rideInStatus(RideStatus.REQUEST);

        assertThatThrownBy(() -> ride.transitionTo(RideStatus.COMPLETE, 42L))
                .isInstanceOf(InvalidRideTransitionException.class)
                .hasMessageContaining("REQUEST")
                .hasMessageContaining("COMPLETE");
    }

    @Test
    @DisplayName("Transicao invalida nao altera estado nem relogio logico")
    void shouldLeaveRideUntouchedWhenTransitionIsInvalid() {
        Ride ride = rideInStatus(RideStatus.COMPLETE);

        assertThatThrownBy(() -> ride.transitionTo(RideStatus.REQUEST, 99L))
                .isInstanceOf(InvalidRideTransitionException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.COMPLETE);
        assertThat(ride.getLogicalTimestamp()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Transicao para null e recusada")
    void shouldRejectNullTargetStatus() {
        Ride ride = rideInStatus(RideStatus.REQUEST);

        assertThatThrownBy(() -> ride.transitionTo(null, 42L))
                .isInstanceOf(InvalidRideTransitionException.class);
    }

    @Test
    @DisplayName("A excecao carrega os estados de origem e destino para o log estruturado")
    void exceptionShouldExposeFromAndToStates() {
        Ride ride = rideInStatus(RideStatus.IN_TRANSIT);

        assertThatThrownBy(() -> ride.transitionTo(RideStatus.MATCH, 42L))
                .isInstanceOf(InvalidRideTransitionException.class)
                .extracting(
                        e -> ((InvalidRideTransitionException) e).getFrom(),
                        e -> ((InvalidRideTransitionException) e).getTo())
                .containsExactly(RideStatus.IN_TRANSIT, RideStatus.MATCH);
    }

    @Test
    @DisplayName("O fluxo principal completo e aceito em sequencia")
    void shouldWalkTheHappyPath() {
        Ride ride = rideInStatus(RideStatus.REQUEST);

        ride.transitionTo(RideStatus.MATCH, 1L);
        ride.transitionTo(RideStatus.CONFIRM, 2L);
        ride.transitionTo(RideStatus.IN_TRANSIT, 3L);
        ride.transitionTo(RideStatus.COMPLETE, 4L);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.COMPLETE);
        assertThat(ride.getLogicalTimestamp()).isEqualTo(4L);
    }
}
