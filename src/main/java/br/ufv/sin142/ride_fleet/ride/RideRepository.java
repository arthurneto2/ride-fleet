package br.ufv.sin142.ride_fleet.ride;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

    /** Estados em que a corrida ocupa um motorista. */
    Collection<RideStatus> ACTIVE_STATUSES = List.of(RideStatus.MATCH, RideStatus.CONFIRM, RideStatus.IN_TRANSIT);

    List<Ride> findByStatus(RideStatus status);

    /** Tamanho da fila de pendentes, usado pela politica de overflow. */
    long countByStatus(RideStatus status);

    /** Pool local de corridas pendentes, em ordem de chegada (FIFO). */
    List<Ride> findByStatusOrderByCreatedAtAsc(RideStatus status);

    /** Corridas que atingiram overflow e aguardam delegacao (fila de saida da Semana 3). */
    List<Ride> findByStatusAndAwaitingDelegationTrueOrderByCreatedAtAsc(RideStatus status);

    List<Ride> findByPassengerIdOrderByCreatedAtDesc(UUID passengerId);

    List<Ride> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

    Page<Ride> findByStatus(RideStatus status, Pageable pageable);

    /** Verifica se o motorista esta comprometido com alguma corrida. */
    boolean existsByDriverIdAndStatusIn(UUID driverId, Collection<RideStatus> statuses);

    /**
     * Trava a linha da corrida antes de ler o status, serializando o
     * ler-verificar-escrever da transicao entre instancias.
     *
     * Aqui nao se usa SKIP LOCKED: quando duas instancias disputam a MESMA
     * corrida, a segunda deve esperar e reavaliar, nao pular.
     *
     * Ordem de lock do projeto: Ride primeiro, Driver depois. Sempre. Ordem
     * invertida em algum ponto gera deadlock que so aparece sob carga.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") UUID id);
}
