package br.ufv.sin142.ride_fleet.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "O email nao pode ser vazio")
    @Email(message = "Formato de email invalido")
    private String email;

    @NotBlank(message = "A senha nao pode ser vazia")
    private String password;
}
