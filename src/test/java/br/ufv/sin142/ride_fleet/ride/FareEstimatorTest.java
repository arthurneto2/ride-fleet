package br.ufv.sin142.ride_fleet.ride;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do estimador de preco e ETA.
 *
 * O estimador nao depende de motorista de proposito: a Semana 3 precisa cotar
 * preco e ETA em {@code POST /api/v1/delegation/bid} para uma corrida que este
 * servico nunca viu.
 */
class FareEstimatorTest {

    /** Tarifas neutras: o preco final fica igual a distancia em km. */
    private FareEstimator estimatorMeasuringDistanceOnly() {
        FareProperties props = new FareProperties();
        props.setBaseFare(new BigDecimal("0.00"));
        props.setPerKm(new BigDecimal("1.00"));
        props.setPerMinute(new BigDecimal("0.00"));
        props.setMinimumFare(new BigDecimal("0.00"));
        props.setAverageSpeedKmh(60.0);
        return new FareEstimator(props);
    }

    private FareEstimator estimatorWithRealisticFares() {
        FareProperties props = new FareProperties();
        props.setBaseFare(new BigDecimal("5.00"));
        props.setPerKm(new BigDecimal("2.50"));
        props.setPerMinute(new BigDecimal("0.50"));
        props.setMinimumFare(new BigDecimal("8.00"));
        props.setAverageSpeedKmh(30.0);
        return new FareEstimator(props);
    }

    @Test
    @DisplayName("Um grau de latitude equivale a aproximadamente 111 km")
    void shouldComputeHaversineDistance() {
        FareQuote quote = estimatorMeasuringDistanceOnly().estimate(0.0, 0.0, 1.0, 0.0);

        // com as tarifas neutras, o preco e a propria distancia em km
        assertThat(quote.price().doubleValue()).isCloseTo(111.19, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    @DisplayName("Origem igual ao destino cobra a tarifa minima")
    void shouldChargeMinimumFareWhenOriginEqualsDestination() {
        FareQuote quote = estimatorWithRealisticFares().estimate(-19.2012, -46.2231, -19.2012, -46.2231);

        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(quote.etaSeconds()).isZero();
    }

    @Test
    @DisplayName("Corrida curta dentro do campus cai na tarifa minima")
    void shouldApplyMinimumFareOnShortRide() {
        // os dois pontos do exemplo da spec ficam a cerca de 0,72 km
        FareQuote quote = estimatorWithRealisticFares()
                .estimate(-19.2012, -46.2231, -19.2045, -46.2290);

        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(quote.etaSeconds()).isBetween(80, 95);
    }

    @Test
    @DisplayName("Preco longo soma tarifa base, por km e por minuto")
    void shouldComposePriceFromBaseDistanceAndTime() {
        // 1 grau de latitude = ~111,19 km; a 30 km/h sao ~3,71 h = ~222,4 min
        FareQuote quote = estimatorWithRealisticFares().estimate(0.0, 0.0, 1.0, 0.0);

        // 5,00 + 2,50*111,19 + 0,50*222,4 = ~394,18
        assertThat(quote.price().doubleValue()).isCloseTo(394.18, org.assertj.core.data.Offset.offset(2.0));
    }

    @Test
    @DisplayName("ETA cresce com a distancia")
    void etaShouldGrowWithDistance() {
        FareEstimator estimator = estimatorWithRealisticFares();

        FareQuote shortRide = estimator.estimate(0.0, 0.0, 0.1, 0.0);
        FareQuote longRide = estimator.estimate(0.0, 0.0, 1.0, 0.0);

        assertThat(longRide.etaSeconds()).isGreaterThan(shortRide.etaSeconds());
    }

    @Test
    @DisplayName("Preco cresce com a distancia")
    void priceShouldGrowWithDistance() {
        FareEstimator estimator = estimatorWithRealisticFares();

        FareQuote shortRide = estimator.estimate(0.0, 0.0, 0.5, 0.0);
        FareQuote longRide = estimator.estimate(0.0, 0.0, 1.0, 0.0);

        assertThat(longRide.price()).isGreaterThan(shortRide.price());
    }

    @Test
    @DisplayName("Preco e arredondado para duas casas decimais")
    void priceShouldHaveTwoDecimalPlaces() {
        FareQuote quote = estimatorWithRealisticFares().estimate(0.0, 0.0, 0.37, 0.041);

        assertThat(quote.price().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("A distancia e simetrica: ida e volta custam o mesmo")
    void estimateShouldBeSymmetric() {
        FareEstimator estimator = estimatorWithRealisticFares();

        FareQuote forward = estimator.estimate(-19.2012, -46.2231, -19.5000, -46.5000);
        FareQuote backward = estimator.estimate(-19.5000, -46.5000, -19.2012, -46.2231);

        assertThat(forward.price()).isEqualByComparingTo(backward.price());
        assertThat(forward.etaSeconds()).isEqualTo(backward.etaSeconds());
    }
}
