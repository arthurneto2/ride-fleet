package br.ufv.sin142.ride_fleet.ride.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRequestDTO {

    /**
     * Presente no contrato documentado em api_spec.md secao 3.2, mas a identidade
     * real vem sempre do token JWT. Se vier preenchido e divergir do token, a
     * resposta e 403 - honrar o id do corpo permitiria pedir corrida em nome de
     * outra pessoa trocando um campo do JSON.
     */
    private UUID passengerId;

    @NotNull(message = "A origem e obrigatoria")
    @Valid
    private LocationDTO origin;

    @NotNull(message = "O destino e obrigatorio")
    @Valid
    private LocationDTO destination;
}
