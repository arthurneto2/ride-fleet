package br.ufv.sin142.ride_fleet.overflow;

import br.ufv.sin142.ride_fleet.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da janela deslizante de latencia que alimenta a politica de overflow.
 *
 * O ponto central do desenho esta no teste shouldNotReportAnAverageBelowMinimumSamples:
 * "sem dados" nao pode ser confundido com "rapido" nem com "lento".
 */
class LatencyWindowTest {

    private static final int CAPACITY = 8;
    private static final int MIN_SAMPLES = 3;

    private MutableClock clock;
    private LatencyWindow window;

    @BeforeEach
    void setUp() {
        OverflowProperties props = new OverflowProperties();
        props.setLatencyWindow(Duration.ofSeconds(30));
        props.setLatencyWindowMinSamples(MIN_SAMPLES);
        props.setLatencyWindowCapacity(CAPACITY);

        clock = new MutableClock(Instant.parse("2026-05-10T12:00:00Z"));
        window = new LatencyWindow(props, clock);
    }

    @Test
    @DisplayName("Janela vazia nao reporta media")
    void shouldNotReportAnAverageWhenEmpty() {
        assertThat(window.averageMillis()).isEmpty();
        assertThat(window.sampleCount()).isZero();
    }

    @Test
    @DisplayName("Abaixo do minimo de amostras nao reporta media")
    void shouldNotReportAnAverageBelowMinimumSamples() {
        window.record(5000);
        window.record(5000);

        // duas amostras lentissimas, mas ainda nao ha base estatistica:
        // reportar media aqui faria o servico delegar por causa de um cold start
        assertThat(window.sampleCount()).isEqualTo(2);
        assertThat(window.averageMillis()).isEmpty();
    }

    @Test
    @DisplayName("Atingido o minimo de amostras, reporta a media")
    void shouldReportAverageOnceMinimumSamplesReached() {
        window.record(100);
        window.record(100);
        window.record(100);

        assertThat(window.averageMillis()).hasValue(100.0);
    }

    @Test
    @DisplayName("A media e aritmetica sobre as amostras da janela")
    void shouldComputeArithmeticMean() {
        window.record(100);
        window.record(200);
        window.record(300);
        window.record(400);

        assertThat(window.averageMillis()).hasValue(250.0);
    }

    @Test
    @DisplayName("Amostras mais velhas que a janela sao descartadas")
    void shouldDiscardSamplesOlderThanTheWindow() {
        window.record(900);
        window.record(900);
        window.record(900);
        assertThat(window.averageMillis()).hasValue(900.0);

        clock.advance(Duration.ofSeconds(31));

        assertThat(window.sampleCount()).isZero();
        assertThat(window.averageMillis()).isEmpty();
    }

    @Test
    @DisplayName("A media considera apenas as amostras ainda dentro da janela")
    void shouldAverageOnlySamplesInsideTheWindow() {
        window.record(1000);
        window.record(1000);

        clock.advance(Duration.ofSeconds(31));

        window.record(50);
        window.record(50);
        window.record(50);

        // as duas amostras de 1000ms sairam da janela
        assertThat(window.sampleCount()).isEqualTo(3);
        assertThat(window.averageMillis()).hasValue(50.0);
    }

    @Test
    @DisplayName("O buffer circular nao cresce alem da capacidade")
    void shouldNotGrowBeyondCapacity() {
        for (int i = 0; i < CAPACITY + 100; i++) {
            window.record(10);
        }

        assertThat(window.sampleCount()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("Ao dar a volta, o buffer mantem as amostras mais recentes")
    void shouldKeepMostRecentSamplesAfterWrapAround() {
        for (int i = 0; i < CAPACITY; i++) {
            window.record(1000);
        }
        for (int i = 0; i < CAPACITY; i++) {
            window.record(20);
        }

        assertThat(window.sampleCount()).isEqualTo(CAPACITY);
        assertThat(window.averageMillis()).hasValue(20.0);
    }

    @Test
    @DisplayName("Registros concorrentes nao corrompem a janela")
    void shouldBeThreadSafe() throws InterruptedException {
        OverflowProperties props = new OverflowProperties();
        props.setLatencyWindow(Duration.ofSeconds(30));
        props.setLatencyWindowMinSamples(1);
        props.setLatencyWindowCapacity(512);
        LatencyWindow concurrentWindow = new LatencyWindow(props, clock);

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 1000; j++) {
                        concurrentWindow.record(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(concurrentWindow.sampleCount()).isEqualTo(512);
        assertThat(concurrentWindow.averageMillis()).hasValue(100.0);
    }
}
