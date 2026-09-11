package br.ufv.sin142.ride_fleet.clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do relogio logico de Lamport.
 *
 * Regra da spec (api_spec.md secao 4): o contador incrementa a cada evento e,
 * ao receber uma mensagem, passa a ser max(local, recebido) + 1.
 */
class LamportClockTest {

    @Test
    @DisplayName("O relogio comeca em zero")
    void shouldStartAtZero() {
        assertThat(new LamportClock().current()).isZero();
    }

    @Test
    @DisplayName("Cada evento local incrementa o contador em um")
    void tickShouldIncrementByOne() {
        LamportClock clock = new LamportClock();

        assertThat(clock.tick()).isEqualTo(1L);
        assertThat(clock.tick()).isEqualTo(2L);
        assertThat(clock.tick()).isEqualTo(3L);
        assertThat(clock.current()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Timestamp recebido maior que o local avanca o relogio para recebido + 1")
    void updateShouldJumpForwardWhenReceivedIsAhead() {
        LamportClock clock = new LamportClock();
        clock.tick(); // local = 1

        assertThat(clock.update(10L)).isEqualTo(11L);
        assertThat(clock.current()).isEqualTo(11L);
    }

    @Test
    @DisplayName("Timestamp recebido menor que o local apenas incrementa o local")
    void updateShouldIncrementLocalWhenReceivedIsBehind() {
        LamportClock clock = new LamportClock();
        clock.update(50L); // local = 51

        assertThat(clock.update(3L)).isEqualTo(52L);
    }

    @Test
    @DisplayName("Timestamp recebido igual ao local incrementa em um")
    void updateShouldIncrementWhenReceivedEqualsLocal() {
        LamportClock clock = new LamportClock();
        clock.update(9L); // local = 10

        assertThat(clock.update(10L)).isEqualTo(11L);
    }

    @Test
    @DisplayName("O relogio nunca retrocede")
    void clockShouldNeverGoBackwards() {
        LamportClock clock = new LamportClock();
        clock.update(100L);

        long before = clock.current();
        clock.update(1L);
        clock.update(2L);
        clock.tick();

        assertThat(clock.current()).isGreaterThan(before);
    }

    @Test
    @DisplayName("Eventos concorrentes nunca recebem o mesmo timestamp")
    void concurrentTicksShouldProduceUniqueTimestamps() throws InterruptedException {
        LamportClock clock = new LamportClock();
        int threads = 16;
        int ticksPerThread = 200;

        Set<Long> seen = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < ticksPerThread; j++) {
                        seen.add(clock.tick());
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

        // se houvesse perda de atualizacao, haveria timestamps repetidos
        assertThat(seen).hasSize(threads * ticksPerThread);
        assertThat(clock.current()).isEqualTo((long) threads * ticksPerThread);
    }

    @Test
    @DisplayName("Atualizacoes concorrentes mantem o relogio monotonico")
    void concurrentUpdatesShouldStayMonotonic() throws InterruptedException {
        LamportClock clock = new LamportClock();
        int threads = 8;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            final long received = i * 10L;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 100; j++) {
                        clock.update(received);
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

        // 800 eventos, cada um avanca o relogio em pelo menos 1
        assertThat(clock.current()).isGreaterThanOrEqualTo(800L);
    }
}
