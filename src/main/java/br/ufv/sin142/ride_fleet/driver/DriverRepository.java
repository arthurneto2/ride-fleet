package br.ufv.sin142.ride_fleet.driver;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    /**
     * Motoristas num dado status que ainda estao ativos.
     *
     * O filtro de active e obrigatorio aqui: sem ele um motorista excluido
     * logicamente volta a ser candidato a receber corridas.
     */
    List<Driver> findByStatusAndActiveTrue(DriverStatus status);

    /**
     * Contagem que alimenta a politica de overflow.
     *
     * Sem o filtro de active o servico acreditaria ter motoristas que nao pode
     * usar e nunca delegaria. E o filtro de maior impacto do projeto.
     */
    long countByStatusAndActiveTrue(DriverStatus status);

    Optional<Driver> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Sem filtro de active de proposito: a restricao de unicidade do banco vale
     * tambem para linhas inativas, e filtrar aqui faria a checagem de cadastro
     * devolver 500 pela constraint em vez de um 400 limpo.
     */
    boolean existsByVehiclePlate(String vehiclePlate);
}
