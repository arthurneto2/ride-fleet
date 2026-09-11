package br.ufv.sin142.ride_fleet.driver.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationDTO {

    @NotNull(message = "A latitude e obrigatoria")
    @DecimalMin(value = "-90.0", message = "Latitude invalida")
    @DecimalMax(value = "90.0", message = "Latitude invalida")
    private Double latitude;

    @NotNull(message = "A longitude e obrigatoria")
    @DecimalMin(value = "-180.0", message = "Longitude invalida")
    @DecimalMax(value = "180.0", message = "Longitude invalida")
    private Double longitude;
}
