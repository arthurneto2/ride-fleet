package br.ufv.sin142.ride_fleet.overflow;

import br.ufv.sin142.ride_fleet.driver.DriverRepository;
import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import br.ufv.sin142.ride_fleet.ride.RideRepository;
import br.ufv.sin142.ride_fleet.ride.RideStatus;
import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Decide se o servico esta congestionado e, portanto, se deve delegar corridas.
 *
 * Componente proprio em vez de um if dentro do RideService porque a mesma regra
 * e lida em quatro pontos do cronograma: gatilho da delegacao (Semana 3),
 * DEGRADED do /health (Semana 2), metrica de estado no Grafana (Semana 5) e o
 * que o professor forca na demo (Semana 7).
 *
 * Precedencia dos motivos, fixa e documentada para que o log e a narrativa da
 * apresentacao sejam legiveis: falta de motorista, depois fila, depois latencia.
 */
@Component
public class OverflowPolicy {

    private final DriverRepository driverRepository;
    private final RideRepository rideRepository;
    private final LatencyWindow latencyWindow;
    private final OverflowProperties properties;

    public OverflowPolicy(DriverRepository driverRepository,
                          RideRepository rideRepository,
                          LatencyWindow latencyWindow,
                          OverflowProperties properties) {
        this.driverRepository = driverRepository;
        this.rideRepository = rideRepository;
        this.latencyWindow = latencyWindow;
        this.properties = properties;
    }

    public OverflowDecision evaluate() {
        // curto-circuito antes de tocar o banco: desligada, a politica custa zero
        if (!properties.isEnabled()) {
            return new OverflowDecision(false, OverflowReason.DISABLED, 0L, 0L, null);
        }

        long availableDrivers = driverRepository.countByStatusAndActiveTrue(DriverStatus.AVAILABLE);
        long pendingRides = rideRepository.countByStatus(RideStatus.REQUEST);

        OptionalDouble averageLatency = latencyWindow.averageMillis();
        Double averageLatencyMs = averageLatency.isPresent() ? averageLatency.getAsDouble() : null;

        OverflowReason reason = firstTriggeredReason(availableDrivers, pendingRides, averageLatency);

        return new OverflowDecision(
                reason != OverflowReason.NONE,
                reason,
                availableDrivers,
                pendingRides,
                averageLatencyMs);
    }

    private OverflowReason firstTriggeredReason(long availableDrivers,
                                                long pendingRides,
                                                OptionalDouble averageLatency) {
        if (availableDrivers == 0L) {
            return OverflowReason.NO_AVAILABLE_DRIVERS;
        }
        if (pendingRides > properties.getPendingRidesThreshold()) {
            return OverflowReason.PENDING_QUEUE_ABOVE_THRESHOLD;
        }
        // ausencia de leitura nunca dispara overflow: nao ha base para julgar
        if (averageLatency.isPresent()
                && averageLatency.getAsDouble() > properties.getAverageLatencyThresholdMs()) {
            return OverflowReason.HIGH_AVERAGE_LATENCY;
        }
        return OverflowReason.NONE;
    }
}
