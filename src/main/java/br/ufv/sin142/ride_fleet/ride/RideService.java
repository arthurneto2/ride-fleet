package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.clock.LamportClock;
import br.ufv.sin142.ride_fleet.driver.Driver;
import br.ufv.sin142.ride_fleet.driver.DriverRepository;
import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import br.ufv.sin142.ride_fleet.overflow.OverflowDecision;
import br.ufv.sin142.ride_fleet.overflow.OverflowPolicy;
import br.ufv.sin142.ride_fleet.passenger.Passenger;
import br.ufv.sin142.ride_fleet.passenger.PassengerRepository;
import br.ufv.sin142.ride_fleet.ride.dto.LocationDTO;
import br.ufv.sin142.ride_fleet.ride.dto.RideRequestDTO;
import br.ufv.sin142.ride_fleet.ride.dto.RideResponseDTO;
import br.ufv.sin142.ride_fleet.security.AuthenticatedUser;
import br.ufv.sin142.ride_fleet.security.JwtService;
import br.ufv.sin142.ride_fleet.shared.exception.ForbiddenOperationException;
import br.ufv.sin142.ride_fleet.shared.exception.IdentityMismatchException;
import br.ufv.sin142.ride_fleet.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Ciclo de vida da corrida: solicitacao, atribuicao e acoes de motorista e
 * passageiro.
 *
 * Toda mudanca de estado passa por {@link Ride#transitionTo}, que valida contra a
 * maquina de estados, e estampa um relogio logico de Lamport.
 *
 * ORDEM DE LOCK DO PROJETO: Ride primeiro, Driver depois. Sempre. Ordem invertida
 * em qualquer ponto gera deadlock que so aparece sob carga.
 *
 * NENHUMA CHAMADA DE REDE dentro dos metodos transacionais daqui: o lock de linha
 * do motorista dura ate o commit.
 */
@Service
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final DriverRepository driverRepository;
    private final PassengerRepository passengerRepository;
    private final FareEstimator fareEstimator;
    private final LamportClock lamportClock;
    private final OverflowPolicy overflowPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public RideService(RideRepository rideRepository,
                       DriverRepository driverRepository,
                       PassengerRepository passengerRepository,
                       FareEstimator fareEstimator,
                       LamportClock lamportClock,
                       OverflowPolicy overflowPolicy,
                       ApplicationEventPublisher eventPublisher) {
        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
        this.passengerRepository = passengerRepository;
        this.fareEstimator = fareEstimator;
        this.lamportClock = lamportClock;
        this.overflowPolicy = overflowPolicy;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Solicitacao
    // ------------------------------------------------------------------

    /**
     * Cria a corrida e tenta atende-la localmente.
     *
     * Em overflow a corrida NAO e rejeitada nem marcada como DELEGATED: ela
     * permanece em REQUEST no pool local com a marca de delegacao pendente. Se um
     * motorista ficar livre antes de o Core assumir, ela ainda e atendida aqui.
     *
     * A atribuicao acontece de forma sincrona dentro da requisicao, o que e
     * aceitavel na Semana 1. A Semana 2 move isso para a fila assincrona.
     */
    @Transactional
    public RideResponseDTO requestRide(RideRequestDTO dto, AuthenticatedUser caller) {
        if (dto.getPassengerId() != null && !dto.getPassengerId().equals(caller.id())) {
            throw new IdentityMismatchException(caller.id(), dto.getPassengerId());
        }

        Passenger passenger = passengerRepository.findById(caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("Passageiro", caller.id()));

        FareQuote quote = fareEstimator.estimate(
                dto.getOrigin().getLatitude(), dto.getOrigin().getLongitude(),
                dto.getDestination().getLatitude(), dto.getDestination().getLongitude());

        Ride ride = Ride.builder()
                .passenger(passenger)
                .status(RideStatus.REQUEST)
                .originLatitude(dto.getOrigin().getLatitude())
                .originLongitude(dto.getOrigin().getLongitude())
                .originAddress(dto.getOrigin().getAddress())
                .destinationLatitude(dto.getDestination().getLatitude())
                .destinationLongitude(dto.getDestination().getLongitude())
                .destinationAddress(dto.getDestination().getAddress())
                .price(quote.price())
                .etaSeconds(quote.etaSeconds())
                .logicalTimestamp(lamportClock.tick())
                .awaitingDelegation(false)
                .build();

        Ride saved = rideRepository.save(ride);

        OverflowDecision decision = overflowPolicy.evaluate();
        if (decision.overflow()) {
            saved.markAwaitingDelegation(decision.reason());
            rideRepository.save(saved);

            log.warn("Corrida {} em overflow ({}): motoristas={} pendentes={} latencia={}ms",
                    saved.getId(), decision.reason(), decision.availableDrivers(),
                    decision.pendingRides(), decision.averageLatencyMs());

            // PONTO DE LIGACAO DA SEMANA 3: um listener AFTER_COMMIT deste evento
            // chama o Core para abrir o leilao. Nada mais aqui precisa mudar.
            eventPublisher.publishEvent(
                    new RideOverflowedEvent(saved.getId(), decision.reason(), saved.getLogicalTimestamp()));

            return toDto(saved);
        }

        tryAssignDriver(saved.getId());
        return toDto(saved);
    }

    /**
     * Reserva um motorista livre e move a corrida para MATCH.
     *
     * Duas protecoes distintas, ambas necessarias:
     *
     * 1. A CORRIDA e travada primeiro (findByIdForUpdate, sem SKIP LOCKED: aqui a
     *    segunda instancia deve ESPERAR e reavaliar). Sem isso, duas instancias
     *    processando a MESMA corrida reservariam dois motoristas e um ficaria
     *    ocupado sem corrida.
     *
     * 2. O MOTORISTA e reservado por UPDATE atomico condicional (claimDriver): o
     *    WHERE status = AVAILABLE e o lock. Retorno 0 significa que outra instancia
     *    chegou primeiro, e entao tentamos o proximo candidato da lista - em vez de
     *    todos disputarem a mesma linha.
     *
     * A lista de candidatos e lida SEM lock, de proposito: nada fica travado
     * durante a selecao.
     *
     * NENHUMA chamada de rede aqui dentro: a transacao mantem lock de linha.
     *
     * @return true se um motorista foi atribuido
     */
    @Transactional
    public boolean tryAssignDriver(UUID rideId) {
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Corrida", rideId));

        if (ride.getStatus() != RideStatus.REQUEST) {
            return false; // outra instancia ja cuidou desta corrida
        }

        Driver claimed = claimAnyAvailableDriver();
        if (claimed == null) {
            return false;
        }

        ride.setDriver(claimed);
        ride.clearAwaitingDelegation();
        ride.transitionTo(RideStatus.MATCH, nextTimestamp(ride));
        rideRepository.save(ride);

        log.info("Corrida {} atribuida ao motorista {} (ts={})",
                ride.getId(), claimed.getId(), ride.getLogicalTimestamp());
        return true;
    }

    /**
     * Percorre os motoristas livres tentando reservar um. O primeiro UPDATE
     * condicional que afetar uma linha venceu a disputa.
     *
     * @return o motorista reservado, ou {@code null} se nenhum restou
     */
    private Driver claimAnyAvailableDriver() {
        List<Driver> candidates = driverRepository.findByStatusAndActiveTrue(DriverStatus.AVAILABLE);

        for (Driver candidate : candidates) {
            int claimed = driverRepository.claimDriver(
                    candidate.getId(), DriverStatus.AVAILABLE, DriverStatus.IN_RIDE);

            if (claimed == 1) {
                // claimDriver passa por fora do contexto de persistencia e o limpa,
                // entao a entidade precisa ser relida para nao ficar desatualizada
                return driverRepository.findById(candidate.getId()).orElseThrow(
                        () -> new ResourceNotFoundException("Motorista", candidate.getId()));
            }
        }
        return null;
    }

    /** Drena o pool de pendentes, usado quando um motorista fica disponivel. */
    @Transactional
    public int drainPendingPool() {
        List<Ride> pending = rideRepository.findByStatusOrderByCreatedAtAsc(RideStatus.REQUEST);
        int assigned = 0;
        for (Ride ride : pending) {
            if (tryAssignDriver(ride.getId())) {
                assigned++;
            } else {
                break; // sem motorista livre, nao ha por que continuar
            }
        }
        return assigned;
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public RideResponseDTO getRideForCaller(UUID rideId, AuthenticatedUser caller) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Corrida", rideId));
        assertCanAccess(ride, caller);
        return toDto(ride);
    }

    @Transactional(readOnly = true)
    public List<RideResponseDTO> listForPassenger(UUID passengerId) {
        return rideRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RideResponseDTO> listForDriver(UUID driverId) {
        return rideRepository.findByDriverIdOrderByCreatedAtDesc(driverId)
                .stream().map(this::toDto).toList();
    }

    /** Pool local de corridas pendentes, em ordem de chegada. */
    @Transactional(readOnly = true)
    public List<RideResponseDTO> listPendingPool() {
        return rideRepository.findByStatusOrderByCreatedAtAsc(RideStatus.REQUEST)
                .stream().map(this::toDto).toList();
    }

    // ------------------------------------------------------------------
    // Acoes do motorista
    // ------------------------------------------------------------------

    @Transactional
    public RideResponseDTO acceptByDriver(UUID rideId, AuthenticatedUser caller) {
        Ride ride = lockRideForAssignedDriver(rideId, caller);
        ride.transitionTo(RideStatus.CONFIRM, nextTimestamp(ride));
        return toDto(rideRepository.save(ride));
    }

    /** Recusa: a corrida volta ao pool local e o motorista e liberado. */
    @Transactional
    public RideResponseDTO declineByDriver(UUID rideId, AuthenticatedUser caller) {
        Ride ride = lockRideForAssignedDriver(rideId, caller);

        Driver driver = ride.getDriver();
        ride.transitionTo(RideStatus.REQUEST, nextTimestamp(ride));
        ride.setDriver(null);
        releaseDriver(driver);

        return toDto(rideRepository.save(ride));
    }

    @Transactional
    public RideResponseDTO startByDriver(UUID rideId, AuthenticatedUser caller) {
        Ride ride = lockRideForAssignedDriver(rideId, caller);
        ride.transitionTo(RideStatus.IN_TRANSIT, nextTimestamp(ride));
        return toDto(rideRepository.save(ride));
    }

    @Transactional
    public RideResponseDTO completeByDriver(UUID rideId, AuthenticatedUser caller) {
        Ride ride = lockRideForAssignedDriver(rideId, caller);
        ride.transitionTo(RideStatus.COMPLETE, nextTimestamp(ride));
        releaseDriver(ride.getDriver());
        return toDto(rideRepository.save(ride));
    }

    // ------------------------------------------------------------------
    // Cancelamento
    // ------------------------------------------------------------------

    @Transactional
    public RideResponseDTO cancelRide(UUID rideId, String reason, AuthenticatedUser caller) {
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Corrida", rideId));
        assertCanAccess(ride, caller);

        ride.transitionTo(RideStatus.CANCELLED, nextTimestamp(ride));
        ride.setCancelReason(reason);
        releaseDriver(ride.getDriver());

        return toDto(rideRepository.save(ride));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    /**
     * Sincroniza o relogio com o timestamp gravado na corrida.
     *
     * Ler a linha compartilhada e, causalmente, receber uma mensagem: a aresta
     * causal passa pelo banco. Sem isso, a instancia B poderia estampar um valor
     * menor do que a instancia A ja escreveu na mesma corrida.
     */
    private long nextTimestamp(Ride ride) {
        Long stored = ride.getLogicalTimestamp();
        return stored == null ? lamportClock.tick() : lamportClock.update(stored);
    }

    private Ride lockRideForAssignedDriver(UUID rideId, AuthenticatedUser caller) {
        Ride ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Corrida", rideId));

        if (ride.getDriver() == null || !ride.getDriver().getId().equals(caller.id())) {
            throw new ForbiddenOperationException("Corrida nao atribuida a este motorista.");
        }
        return ride;
    }

    private void releaseDriver(Driver driver) {
        if (driver != null && driver.getStatus() == DriverStatus.IN_RIDE) {
            driver.setStatus(DriverStatus.AVAILABLE);
            driverRepository.save(driver);
        }
    }

    /**
     * Verificacao de propriedade no service, e nao na controller, porque a mesma
     * regra vale para consultar, cancelar, aceitar, iniciar e concluir. Duplicada
     * em cinco controllers, e onde um furo aparece.
     */
    private void assertCanAccess(Ride ride, AuthenticatedUser caller) {
        if (JwtService.ROLE_ADMIN.equals(caller.role())) {
            return;
        }
        boolean isOwner = ride.getPassenger() != null
                && ride.getPassenger().getId().equals(caller.id());
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getId().equals(caller.id());

        if (!isOwner && !isAssignedDriver) {
            throw new ForbiddenOperationException("Corrida nao pertence ao usuario autenticado.");
        }
    }

    RideResponseDTO toDto(Ride ride) {
        return new RideResponseDTO(
                ride.getId(),
                ride.getPassenger() == null ? null : ride.getPassenger().getId(),
                ride.getDriver() == null ? null : ride.getDriver().getId(),
                ride.getStatus(),
                ride.getDelegatedToGroup(),
                ride.getEtaSeconds(),
                ride.getPrice(),
                ride.getLogicalTimestamp(),
                ride.getAwaitingDelegation(),
                ride.getOverflowReason(),
                LocationDTO.builder()
                        .latitude(ride.getOriginLatitude())
                        .longitude(ride.getOriginLongitude())
                        .address(ride.getOriginAddress())
                        .build(),
                LocationDTO.builder()
                        .latitude(ride.getDestinationLatitude())
                        .longitude(ride.getDestinationLongitude())
                        .address(ride.getDestinationAddress())
                        .build(),
                ride.getCreatedAt(),
                ride.getUpdatedAt());
    }
}
