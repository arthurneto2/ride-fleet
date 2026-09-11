package br.ufv.sin142.ride_fleet.ride.dto;

import br.ufv.sin142.ride_fleet.overflow.OverflowReason;
import br.ufv.sin142.ride_fleet.ride.RideStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resposta de corrida.
 *
 * As chaves de rideId a logicalTimestamp reproduzem exatamente o contrato de
 * api_spec.md secao 3.2 - inclusive o nome rideId em vez de id. Os campos
 * seguintes sao aditivos: os testes de contrato dos outros grupos verificam
 * apenas as chaves documentadas.
 */
public record RideResponseDTO(
        UUID rideId,
        UUID passengerId,
        UUID driverId,
        RideStatus status,
        String delegatedToGroup,
        Integer etaSeconds,
        BigDecimal price,
        Long logicalTimestamp,
        Boolean awaitingDelegation,
        OverflowReason overflowReason,
        LocationDTO origin,
        LocationDTO destination,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
