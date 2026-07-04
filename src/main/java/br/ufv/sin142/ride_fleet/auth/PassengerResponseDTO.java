package br.ufv.sin142.ride_fleet.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerResponseDTO {
    private UUID id;
    private String name;
    private String email;
    private String phone;
}
