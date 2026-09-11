package br.ufv.sin142.ride_fleet.ride;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Parametros de tarifacao, externalizados para que o grupo possa ajustar precos
 * na demo sem recompilar.
 */
@Component
@ConfigurationProperties(prefix = "ridefleet.fare")
@Getter
@Setter
public class FareProperties {

    /** Valor fixo cobrado em toda corrida. */
    private BigDecimal baseFare = new BigDecimal("5.00");

    /** Valor cobrado por quilometro percorrido. */
    private BigDecimal perKm = new BigDecimal("2.50");

    /** Valor cobrado por minuto estimado de viagem. */
    private BigDecimal perMinute = new BigDecimal("0.50");

    /** Piso de preco: nenhuma corrida custa menos que isso. */
    private BigDecimal minimumFare = new BigDecimal("8.00");

    /** Velocidade media usada para estimar o ETA a partir da distancia. */
    private double averageSpeedKmh = 30.0;
}
