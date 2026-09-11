package br.ufv.sin142.ride_fleet.driver;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
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

    Page<Driver> findByActiveTrue(Pageable pageable);

    /**
     * Seleciona e reserva candidatos para atribuicao de corrida, num unico passo.
     *
     * Emite SELECT ... FOR UPDATE SKIP LOCKED. Verificado: o H2 2.4.240 suporta
     * SKIP LOCKED e o H2Dialect do Hibernate 7.4 habilita a opcao, entao esta
     * unica query roda identica em PostgreSQL e nos testes.
     *
     * Por que SKIP LOCKED e nao UPDATE condicional: a instancia B pula a linha
     * que A travou e pega o proximo motorista na primeira tentativa. Com UPDATE
     * condicional, B perderia e teria de tentar de novo - sob carga isso vira
     * tempestade de retentativas exatamente na demo de load balancer.
     *
     * ATENCAO: o lock dura ate o commit. Nenhuma chamada de rede pode acontecer
     * dentro desta transacao, sob pena de um parceiro lento segurar um lock de
     * linha do PostgreSQL pelo timeout inteiro.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select d from Driver d where d.status = :status and d.active = true order by d.id")
    List<Driver> findAvailableForAssignment(@Param("status") DriverStatus status, Limit limit);
}
