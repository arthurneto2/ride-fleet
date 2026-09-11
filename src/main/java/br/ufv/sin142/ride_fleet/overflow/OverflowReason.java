package br.ufv.sin142.ride_fleet.overflow;

/**
 * Motivo pelo qual o servico se considera (ou nao) congestionado.
 *
 * Existe como enum, e nao como string, porque a Semana 2 exige log estruturado
 * dizendo por que o servico degradou e a Semana 5 transforma isso em metrica.
 */
public enum OverflowReason {

    /** Servico saudavel. */
    NONE,

    /** Politica desligada por configuracao. */
    DISABLED,

    /** Nenhum motorista ativo em AVAILABLE. */
    NO_AVAILABLE_DRIVERS,

    /** Corridas pendentes acima do limite configurado. */
    PENDING_QUEUE_ABOVE_THRESHOLD,

    /** Latencia media recente acima do limite configurado. */
    HIGH_AVERAGE_LATENCY
}
