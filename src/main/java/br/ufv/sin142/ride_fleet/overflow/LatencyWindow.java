package br.ufv.sin142.ride_fleet.overflow;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.OptionalDouble;

/**
 * Janela deslizante da latencia recente dos endpoints de corrida.
 *
 * Buffer circular de tamanho fixo: memoria constante, nenhuma alocacao no
 * caminho quente e descarte natural das amostras velhas. Uma lista que cresce
 * seria um vazamento de memoria exatamente sob o stress test da Semana 7, que
 * e quando menos se quer alocar.
 *
 * A media e por instancia, de proposito: latencia e um sinal de saude local do
 * processo, nao um numero global do cluster.
 */
@Component
public class LatencyWindow {

    private final long windowMillis;
    private final int minSamples;
    private final int capacity;
    private final Clock clock;

    /** Instante de registro de cada amostra, em milissegundos. */
    private final long[] recordedAt;

    /** Duracao de cada amostra, em milissegundos. */
    private final long[] durations;

    private int writeIndex;
    private int size;

    /**
     * Monitor proprio em vez de AtomicLongArray: o par (instante, duracao) e o
     * indice precisam avancar juntos de forma atomica, o que atomicos so dariam
     * com um laco de CAS sobre um long empacotado. Um monitor sem contencao
     * custa dezenas de nanosegundos, contra o round trip de banco de ~1ms que
     * acontece na mesma avaliacao de overflow.
     */
    private final Object lock = new Object();

    public LatencyWindow(OverflowProperties properties, Clock clock) {
        this.windowMillis = properties.getLatencyWindow().toMillis();
        this.minSamples = properties.getLatencyWindowMinSamples();
        this.capacity = properties.getLatencyWindowCapacity();
        this.clock = clock;
        this.recordedAt = new long[capacity];
        this.durations = new long[capacity];
    }

    /** Registra a duracao de uma requisicao. O(1). */
    public void record(long durationMillis) {
        long now = clock.millis();
        synchronized (lock) {
            recordedAt[writeIndex] = now;
            durations[writeIndex] = durationMillis;
            writeIndex = (writeIndex + 1) % capacity;
            if (size < capacity) {
                size++;
            }
        }
    }

    /** Quantidade de amostras ainda dentro da janela. */
    public int sampleCount() {
        long cutoff = clock.millis() - windowMillis;
        synchronized (lock) {
            return countSince(cutoff);
        }
    }

    /**
     * Latencia media das amostras dentro da janela.
     *
     * Devolve vazio enquanto houver menos de {@code latencyWindowMinSamples}
     * amostras. Isso e deliberado: "sem dados" nao pode ser lido como "rapido"
     * (mascararia congestionamento) nem como "lento" (delegaria sem motivo).
     */
    public OptionalDouble averageMillis() {
        long cutoff = clock.millis() - windowMillis;
        synchronized (lock) {
            long sum = 0L;
            int count = 0;
            for (int i = 0; i < size; i++) {
                if (recordedAt[i] >= cutoff) {
                    sum += durations[i];
                    count++;
                }
            }
            if (count < minSamples) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of((double) sum / count);
        }
    }

    private int countSince(long cutoff) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (recordedAt[i] >= cutoff) {
                count++;
            }
        }
        return count;
    }
}
