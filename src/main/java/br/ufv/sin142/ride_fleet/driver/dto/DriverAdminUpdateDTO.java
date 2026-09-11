package br.ufv.sin142.ride_fleet.driver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Atualizacao completa de motorista pelo administrador. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverAdminUpdateDTO {

    @NotBlank(message = "O nome nao pode ser vazio")
    private String name;

    @NotBlank(message = "A placa do veiculo nao pode ser vazia")
    private String vehiclePlate;

    @NotBlank(message = "O email nao pode ser vazio")
    @Email(message = "Formato de email invalido")
    private String email;

    /** Permite reativar um motorista excluido logicamente. */
    private Boolean active;
}
