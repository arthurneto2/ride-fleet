package br.ufv.sin142.ride_fleet.overflow;

/**
 * Resultado de uma avaliacao de congestionamento.
 *
 * Carrega o motivo e os tres sinais, nao apenas um booleano, porque estes sao
 * exatamente os numeros que o /health da Semana 2 precisa devolver
 * (availableDrivers, tamanho da fila, recentLatencyMs) e que o log estruturado
 * precisa registrar.
 *
 * @param averageLatencyMs {@code null} quando ainda nao ha amostras suficientes
 *                         na janela: ausencia de leitura nao e o mesmo que zero.
 */
public record OverflowDecision(
        boolean overflow,
        OverflowReason reason,
        long availableDrivers,
        long pendingRides,
        Double averageLatencyMs) {
}
