package br.ufv.sin142.ride_fleet.auth;

import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponseDTO {
    private UUID id;
    private String name;
    private String vehiclePlate;
    private String email;
    private DriverStatus status;
    private Double currentLatitude;
    private Double currentLongitude;
}
