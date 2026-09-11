package br.ufv.sin142.ride_fleet.admin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ridefleet.admin")
@Getter
@Setter
public class AdminProperties {

    private boolean seedEnabled = true;

    private String name = "Administrador RideFleet";

    private String email = "admin@ridefleet.local";

    /**
     * Deliberadamente vazia: senha padrao fixa versionada no repositorio seria
     * uma credencial conhecida por qualquer um que leia o codigo. Vazia, o seed
     * gera uma senha aleatoria e a registra uma vez em WARN.
     */
    private String password = "";
}
