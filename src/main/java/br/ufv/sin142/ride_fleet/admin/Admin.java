package br.ufv.sin142.ride_fleet.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Administrador do servico.
 *
 * Entidade propria, e nao uma credencial estatica em configuracao, por dois
 * motivos: segue exatamente o padrao que ja existe para passageiro e motorista
 * (BCrypt no banco, login pelo AuthService, token pelo JwtService), sem um segundo
 * caminho de autenticacao para manter; e mantem o subject do JWT sendo um UUID
 * real, preservando a invariante de AuthenticatedUser.
 */
@Entity
@Table(name = "admins")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @JsonIgnore
    private String password;
}
