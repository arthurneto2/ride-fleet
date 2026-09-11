package br.ufv.sin142.ride_fleet.ride;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Estima preco e ETA de uma corrida a partir das coordenadas de origem e destino.
 *
 * Nao depende de motorista de proposito: a Semana 3 precisa cotar uma corrida que
 * este servico nunca viu para responder ao leilao do Core
 * ({@code POST /api/v1/delegation/bid}). Manter uma unica fonte de verdade de
 * preco evita duas logicas de tarifacao divergindo.
 */
@Component
public class FareEstimator {

    /** Raio medio da Terra em quilometros. */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final int SECONDS_PER_HOUR = 3600;

    private final FareProperties properties;

    public FareEstimator(FareProperties properties) {
        this.properties = properties;
    }

    public FareQuote estimate(double originLatitude, double originLongitude,
                              double destinationLatitude, double destinationLongitude) {

        double distanceKm = haversineDistanceKm(
                originLatitude, originLongitude, destinationLatitude, destinationLongitude);

        int etaSeconds = (int) Math.round(distanceKm / properties.getAverageSpeedKmh() * SECONDS_PER_HOUR);

        BigDecimal distanceComponent = properties.getPerKm()
                .multiply(BigDecimal.valueOf(distanceKm));

        BigDecimal timeComponent = properties.getPerMinute()
                .multiply(BigDecimal.valueOf(etaSeconds))
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);

        BigDecimal price = properties.getBaseFare()
                .add(distanceComponent)
                .add(timeComponent)
                .max(properties.getMinimumFare())
                .setScale(2, RoundingMode.HALF_UP);

        return new FareQuote(price, etaSeconds);
    }

    /**
     * Distancia do grande circulo entre dois pontos, pela formula de Haversine.
     * Simetrica: estimar A->B e B->A da o mesmo resultado.
     */
    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(deltaLon / 2), 2);

        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
