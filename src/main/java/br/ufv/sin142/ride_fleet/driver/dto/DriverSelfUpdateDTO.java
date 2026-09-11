package br.ufv.sin142.ride_fleet.driver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Atualizacao parcial do proprio perfil: campo nulo significa "nao alterar". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverSelfUpdateDTO {

    private String name;
    private String vehiclePlate;
}
