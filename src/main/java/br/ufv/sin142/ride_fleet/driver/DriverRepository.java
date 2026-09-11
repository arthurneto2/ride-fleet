package br.ufv.sin142.ride_fleet.driver;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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
     * Reserva um motorista especifico, de forma atomica.
     *
     * O WHERE e o lock: o banco garante que apenas uma transacao consegue mudar a
     * linha de :expected para :to. Retorno 0 significa "outra instancia chegou
     * primeiro" - o chamador tenta o proximo candidato.
     *
     * POR QUE ESTE MECANISMO E NAO SELECT ... FOR UPDATE SKIP LOCKED:
     *
     * O SKIP LOCKED seria teoricamente melhor (evita a retentativa), e o H2 de
     * fato emite a clausula. Mas foi VERIFICADO experimentalmente que, com
     * LIMIT 1, o H2 busca uma linha, descobre que esta travada, pula - e devolve
     * vazio, em vez de avancar para a proxima linha livre como o PostgreSQL faz.
     * Com 10 motoristas livres e 10 threads, apenas 1 conseguia atribuicao.
     *
     * O UPDATE condicional, alem de ser identico nos dois bancos, nao mantem lock
     * durante a SELECAO dos candidatos, e a retentativa e barata: uma ida ao banco
     * que falha, e o chamador ja parte para o proximo candidato da lista - nao
     * fica todo mundo disputando a mesma linha.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Driver d set d.status = :to "
            + "where d.id = :id and d.status = :expected and d.active = true")
    int claimDriver(@Param("id") UUID id,
                    @Param("expected") DriverStatus expected,
                    @Param("to") DriverStatus to);
}
