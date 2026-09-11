package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.driver.Driver;
import br.ufv.sin142.ride_fleet.passenger.Passenger;
import br.ufv.sin142.ride_fleet.shared.exception.InvalidRideTransitionException;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rides")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"passenger", "driver"})
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    /**
     * Sem setter publico de proposito: a unica forma de mudar o estado e
     * {@link #transitionTo(RideStatus, long)}, que valida a transicao. Assim a
     * regra da maquina de estados e garantida pelo compilador, nao por convencao.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private RideStatus status;

    @Column(name = "origin_latitude", nullable = false)
    private Double originLatitude;

    @Column(name = "origin_longitude", nullable = false)
    private Double originLongitude;

    @Column(name = "origin_address", nullable = false)
    private String originAddress;

    @Column(name = "destination_latitude", nullable = false)
    private Double destinationLatitude;

    @Column(name = "destination_longitude", nullable = false)
    private Double destinationLongitude;

    @Column(name = "destination_address", nullable = false)
    private String destinationAddress;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "eta_seconds", nullable = false)
    private Integer etaSeconds;

    @Column(name = "delegated_to_group")
    private String delegatedToGroup;

    @Column(name = "logical_timestamp", nullable = false)
    private Long logicalTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Aplica uma transicao de estado validada pela maquina de estados e estampa o
     * relogio logico de Lamport do evento.
     *
     * @throws InvalidRideTransitionException se a transicao nao for permitida a
     *         partir do estado atual. Nesse caso a entidade nao e alterada.
     */
    public void transitionTo(RideStatus target, long logicalTimestamp) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidRideTransitionException(status, target);
        }
        this.status = target;
        this.logicalTimestamp = logicalTimestamp;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (logicalTimestamp == null) {
            logicalTimestamp = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
