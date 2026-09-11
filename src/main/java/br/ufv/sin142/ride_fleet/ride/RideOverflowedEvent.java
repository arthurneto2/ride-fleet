package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.overflow.OverflowReason;

import java.util.UUID;

/**
 * Emitido quando uma corrida atinge a politica de overflow.
 *
 * Este e o ponto de ligacao com a Semana 3: basta adicionar um
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} que chame o Core
 * para abrir o leilao. Nada mais precisa mudar.
 *
 * AFTER_COMMIT, e nao dentro da transacao, por um motivo concreto: a transacao de
 * atribuicao segura lock de linha no motorista. Uma chamada HTTP ali dentro faria
 * um parceiro lento reter esse lock pelo timeout inteiro; a fila travaria, a
 * latencia subiria, a latencia alta dispararia mais overflow e mais chamadas ao
 * Core - falha metaestavel.
 */
public record RideOverflowedEvent(UUID rideId, OverflowReason reason, long logicalTimestamp) {
}
