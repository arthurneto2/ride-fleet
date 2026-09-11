package br.ufv.sin142.ride_fleet.ride.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRideDTO {

    @Size(max = 255, message = "O motivo deve ter no maximo 255 caracteres")
    private String reason;
}
