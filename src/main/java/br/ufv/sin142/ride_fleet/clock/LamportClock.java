package br.ufv.sin142.ride_fleet.clock;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Relogio logico de Lamport do servico, conforme api_spec.md secao 4.
 *
 * O contador e por processo, de proposito: Lamport define um relogio por
 * processo, nao um contador global. Com duas instancias atras do load balancer
 * cada uma tem o seu, e a ordem causal e preservada porque toda aresta causal
 * passa por uma mensagem (ou pela linha compartilhada no banco), momento em que
 * {@link #update(long)} sincroniza os dois.
 *
 * Consequencia a ter em mente: o contador zera quando o processo reinicia.
 */
@Component
public class LamportClock {

    private final AtomicLong counter = new AtomicLong(0L);

    /** Evento local (ou imediatamente antes de enviar uma mensagem). */
    public long tick() {
        return counter.incrementAndGet();
    }

    /**
     * Evento de recebimento: avanca o relogio para {@code max(local, recebido) + 1}.
     *
     * @param received timestamp logico que veio na mensagem
     * @return o novo valor do relogio, que deve ser estampado no evento
     */
    public long update(long received) {
        return counter.updateAndGet(local -> Math.max(local, received) + 1);
    }

    /** Valor atual, sem gerar evento. */
    public long current() {
        return counter.get();
    }
}
