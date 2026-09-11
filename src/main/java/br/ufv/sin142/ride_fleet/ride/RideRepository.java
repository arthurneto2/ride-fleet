package br.ufv.sin142.ride_fleet.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

    List<Ride> findByStatus(RideStatus status);

    /** Tamanho da fila de pendentes, usado pela politica de overflow. */
    long countByStatus(RideStatus status);

    /** Pool local de corridas pendentes, em ordem de chegada (FIFO). */
    List<Ride> findByStatusOrderByCreatedAtAsc(RideStatus status);
}
