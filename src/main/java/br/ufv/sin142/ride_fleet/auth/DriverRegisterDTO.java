package br.ufv.sin142.ride_fleet.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverRegisterDTO {

    @NotBlank(message = "O nome nao pode ser vazio")
    private String name;

    @NotBlank(message = "A placa do veiculo nao pode ser vazia")
    private String vehiclePlate;

    @NotBlank(message = "O email nao pode ser vazio")
    @Email(message = "Formato de email invalido")
    private String email;

    @NotBlank(message = "A senha nao pode ser vazia")
    @Size(min = 6, message = "A senha deve ter no minimo 6 caracteres")
    private String password;
}
