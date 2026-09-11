package br.ufv.sin142.ride_fleet.overflow;

import br.ufv.sin142.ride_fleet.driver.DriverRepository;
import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import br.ufv.sin142.ride_fleet.ride.RideRepository;
import br.ufv.sin142.ride_fleet.ride.RideStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes da politica de overflow.
 *
 * A regra e lida em quatro lugares do cronograma: dispara a delegacao na
 * Semana 3, define o DEGRADED do /health na Semana 2, virou metrica na Semana 5
 * e e o que o professor forca na demo da Semana 7. Por isso ela vive num
 * componente proprio e tem teste dedicado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverflowPolicyTest {

    private static final int PENDING_THRESHOLD = 5;
    private static final long LATENCY_THRESHOLD_MS = 800L;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private RideRepository rideRepository;

    @Mock
    private LatencyWindow latencyWindow;

    private OverflowProperties properties;
    private OverflowPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new OverflowProperties();
        properties.setEnabled(true);
        properties.setPendingRidesThreshold(PENDING_THRESHOLD);
        properties.setAverageLatencyThresholdMs(LATENCY_THRESHOLD_MS);

        policy = new OverflowPolicy(driverRepository, rideRepository, latencyWindow, properties);
    }

    /** Cenario saudavel: motoristas livres, fila curta, latencia baixa. */
    private void givenHealthyService() {
        lenient().when(driverRepository.countByStatusAndActiveTrue(DriverStatus.AVAILABLE)).thenReturn(3L);
        lenient().when(rideRepository.countByStatus(RideStatus.REQUEST)).thenReturn(1L);
        lenient().when(latencyWindow.averageMillis()).thenReturn(OptionalDouble.of(120.0));
    }

    @Test
    @DisplayName("Servico saudavel nao esta congestionado")
    void shouldNotOverflowWhenHealthy() {
        givenHealthyService();

        OverflowDecision decision = policy.evaluate();

        assertThat(decision.overflow()).isFalse();
        assertThat(decision.reason()).isEqualTo(OverflowReason.NONE);
        assertThat(decision.availableDrivers()).isEqualTo(3L);
        assertThat(decision.pendingRides()).isEqualTo(1L);
        assertThat(decision.averageLatencyMs()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("Sem motoristas disponiveis, o servico esta congestionado")
    void shouldOverflowWhenNoDriversAvailable() {
        givenHealthyService();
        when(driverRepository.countByStatusAndActiveTrue(DriverStatus.AVAILABLE)).thenReturn(0L);

        OverflowDecision decision = policy.evaluate();

        assertThat(decision.overflow()).isTrue();
        assertThat(decision.reason()).isEqualTo(OverflowReason.NO_AVAILABLE_DRIVERS);
    }

    @Test
    @DisplayName("Fila exatamente no limite ainda nao e congestionamento")
    void shouldNotOverflowWhenPendingRidesEqualsThreshold() {
        givenHealthyService();
        when(rideRepository.countByStatus(RideStatus.REQUEST)).thenReturn((long) PENDING_THRESHOLD);

        OverflowDecision decision = policy.evaluate();

        assertThat(decision.overflow()).isFalse();
        assertThat(decision.reason()).isEqualTo(OverflowReason.NONE);
    }

    @Test
    @DisplayName("Fila acima do limite e congestionamento")
    void shouldOverflowWhenPendingRidesAboveThreshold() {
        givenHealthyService();
        when(rideRepository.countByStatus(RideStatus.REQUEST)).thenReturn((long) PENDING_THRESHOLD + 1);

        OverflowDecision decision = policy.evaluate();

        assertThat(decision.overflow()).isTrue();
        assertThat(decision.reason()).isEqualTo(OverflowReason.PENDING_QUEUE_ABOVE_THRESHOLD);
    }

    @Test
    @DisplayName("Latencia exatamente no limite ainda nao e congestionamento")
    void shouldNotOverflowWhenLatencyEqualsThreshold() {
        givenHealthyService();
        when(latencyWindow.averageMillis()).thenReturn(OptionalDouble.of((double) LATENCY_THRESHOLD_MS));

        assertThat(policy.evaluate().overflow()).isFalse();
    }

    @Test
    @DisplayName("Latencia media alta e congestionamento")
    void shouldOverflowWhenLatencyAboveThreshold() {
        givenHealthyService();
        when(latencyWindow.averageMillis()).thenReturn(OptionalDouble.of(LATENCY_THRESHOLD_MS + 1.0));

        OverflowDecision decision = policy.evaluate();

        assertThat(decision.overflow()).isTrue();
        assertThat(decision.reason()).isEqualTo(OverflowReason.HIGH_AVERAGE_LATENCY);
        assertThat(decision.averageLatencyMs()).isEqualTo(LATENCY_THRESHOLD_MS + 1.0);
    }

    @Test
    @DisplayName("Sem amostras de latencia suficientes, a latencia nao dispara overflow")
    void shouldNotOverflowWhenLatencyHasNoReading() {
        givenHealthyService();
        properties.setAverageLatencyThresholdMs(1L); // limite minusculo de proposito
        when(latencyWindow.averageMillis()).thenReturn(OptionalDouble.empty());

        OverflowDecision decision = policy.evaluate();

        assertThat(decision.overflow()).isFalse();
        assertThat(decision.averageLatencyMs()).isNull();
    }

    @Test
    @DisplayName("Desligada, a politica nao consulta o banco nem congestiona")
    void shouldShortCircuitWhenDisabled() {
        properties.setEnabled(false);

        OverflowDecision decision = policy.evaluate();

        assertThat(decision.overflow()).isFalse();
        assertThat(decision.reason()).isEqualTo(OverflowReason.DISABLED);
        verifyNoInteractions(driverRepository, rideRepository, latencyWindow);
    }

    @Test
    @DisplayName("Com varios sinais ativos, o motivo reportado segue a precedencia documentada")
    void shouldReportFirstMatchingReasonByPrecedence() {
        when(driverRepository.countByStatusAndActiveTrue(DriverStatus.AVAILABLE)).thenReturn(0L);
        when(rideRepository.countByStatus(RideStatus.REQUEST)).thenReturn(50L);
        when(latencyWindow.averageMillis()).thenReturn(OptionalDouble.of(5000.0));

        OverflowDecision decision = policy.evaluate();

        assertThat(decision.overflow()).isTrue();
        assertThat(decision.reason()).isEqualTo(OverflowReason.NO_AVAILABLE_DRIVERS);
    }

    @Test
    @DisplayName("A decisao carrega os tres sinais para o log e para o /health")
    void decisionShouldCarryAllSignals() {
        when(driverRepository.countByStatusAndActiveTrue(DriverStatus.AVAILABLE)).thenReturn(0L);
        when(rideRepository.countByStatus(RideStatus.REQUEST)).thenReturn(12L);
        when(latencyWindow.averageMillis()).thenReturn(OptionalDouble.of(333.0));

        OverflowDecision decision = policy.evaluate();

        // a Semana 2 precisa exatamente destes numeros no /health
        assertThat(decision.availableDrivers()).isZero();
        assertThat(decision.pendingRides()).isEqualTo(12L);
        assertThat(decision.averageLatencyMs()).isEqualTo(333.0);
    }
}
