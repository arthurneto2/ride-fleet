package br.ufv.sin142.ride_fleet.driver.dto;

import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mudanca de status do motorista.
 *
 * IN_RIDE e aceito na desserializacao mas recusado pelo servico: e estado
 * derivado, definido pelo matcher ao atribuir uma corrida.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatusUpdateDTO {

    @NotNull(message = "O status e obrigatorio")
    private DriverStatus status;
}
