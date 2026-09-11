package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.driver.Driver;
import br.ufv.sin142.ride_fleet.overflow.OverflowReason;
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

    /**
     * Fila de saida: a corrida atingiu a politica de overflow e aguarda delegacao.
     *
     * Ela permanece em REQUEST no pool local de proposito. Assim, se um motorista
     * ficar livre antes de o Core assumir, a corrida ainda e atendida localmente.
     * A Semana 3 pluga a chamada ao Core exatamente neste ponto.
     */
    @Builder.Default
    @Column(name = "awaiting_delegation", nullable = false)
    private Boolean awaitingDelegation = false;

    /** Motivo do overflow, para o log estruturado da Semana 2. */
    @Enumerated(EnumType.STRING)
    @Column(name = "overflow_reason")
    private OverflowReason overflowReason;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "logical_timestamp", nullable = false)
    private Long logicalTimestamp;

    /**
     * Versao para bloqueio otimista.
     *
     * O motorista e um recurso RESERVADO (daí o SELECT ... FOR UPDATE SKIP LOCKED
     * na atribuicao); a corrida e um recurso EDITADO CONCORRENTEMENTE - motorista
     * aceitando ao mesmo tempo que o passageiro cancela. Dois tipos de disputa,
     * dois mecanismos.
     */
    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

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

    /** Marca a corrida como aguardando delegacao, sem tira-la do pool local. */
    public void markAwaitingDelegation(OverflowReason reason) {
        this.awaitingDelegation = true;
        this.overflowReason = reason;
    }

    /** Limpa a marca de delegacao pendente, quando a corrida e atendida localmente. */
    public void clearAwaitingDelegation() {
        this.awaitingDelegation = false;
        this.overflowReason = null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (logicalTimestamp == null) {
            logicalTimestamp = 0L;
        }
        if (awaitingDelegation == null) {
            awaitingDelegation = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
