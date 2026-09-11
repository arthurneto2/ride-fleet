package br.ufv.sin142.ride_fleet.ride;

import java.math.BigDecimal;

/**
 * Cotacao de uma corrida: preco estimado e tempo estimado em segundos.
 *
 * E o par que {@code GET /api/v1/rides/{id}} devolve ao front-end e que a
 * Semana 3 devolve ao Core como proposta de leilao.
 */
public record FareQuote(BigDecimal price, int etaSeconds) {
}
