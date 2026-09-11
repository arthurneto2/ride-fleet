package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.clock.LamportClock;
import br.ufv.sin142.ride_fleet.driver.Driver;
import br.ufv.sin142.ride_fleet.driver.DriverRepository;
import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import br.ufv.sin142.ride_fleet.overflow.OverflowDecision;
import br.ufv.sin142.ride_fleet.overflow.OverflowPolicy;
import br.ufv.sin142.ride_fleet.overflow.OverflowReason;
import br.ufv.sin142.ride_fleet.passenger.Passenger;
import br.ufv.sin142.ride_fleet.passenger.PassengerRepository;
import br.ufv.sin142.ride_fleet.ride.dto.LocationDTO;
import br.ufv.sin142.ride_fleet.ride.dto.RideRequestDTO;
import br.ufv.sin142.ride_fleet.ride.dto.RideResponseDTO;
import br.ufv.sin142.ride_fleet.security.AuthenticatedUser;
import br.ufv.sin142.ride_fleet.shared.exception.ForbiddenOperationException;
import br.ufv.sin142.ride_fleet.shared.exception.IdentityMismatchException;
import br.ufv.sin142.ride_fleet.shared.exception.InvalidRideTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do servico de corridas.
 *
 * FareEstimator e LamportClock entram reais, nao mockados: sao deterministicos e
 * sem dependencia externa, e testar contra o codigo de verdade vale mais do que
 * testar contra um mock configurado por mim.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private OverflowPolicy overflowPolicy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LamportClock lamportClock;
    private RideService service;

    private Passenger passenger;
    private AuthenticatedUser passengerCaller;

    @BeforeEach
    void setUp() {
        FareProperties fareProperties = new FareProperties();
        FareEstimator fareEstimator = new FareEstimator(fareProperties);
        lamportClock = new LamportClock();

        service = new RideService(rideRepository, driverRepository, passengerRepository,
                fareEstimator, lamportClock, overflowPolicy, eventPublisher);

        passenger = Passenger.builder()
                .id(UUID.randomUUID())
                .name("Joao da Silva")
                .email("joao@exemplo.com")
                .phone("31999998888")
                .password("hash")
                .build();
        passengerCaller = new AuthenticatedUser(passenger.getId(), passenger.getEmail(), "ROLE_PASSENGER");

        when(passengerRepository.findById(passenger.getId())).thenReturn(Optional.of(passenger));
        when(rideRepository.save(any(Ride.class))).thenAnswer(call -> call.getArgument(0));
        when(rideRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(call -> Optional.empty());
        givenNoOverflow();
    }

    private void givenNoOverflow() {
        when(overflowPolicy.evaluate())
                .thenReturn(new OverflowDecision(false, OverflowReason.NONE, 3L, 0L, 100.0));
    }

    private void givenOverflow(OverflowReason reason) {
        when(overflowPolicy.evaluate())
                .thenReturn(new OverflowDecision(true, reason, 0L, 9L, null));
    }

    private void givenAvailableDriver(Driver driver) {
        when(driverRepository.findByStatusAndActiveTrue(DriverStatus.AVAILABLE))
                .thenReturn(List.of(driver));
        when(driverRepository.claimDriver(driver.getId(), DriverStatus.AVAILABLE, DriverStatus.IN_RIDE))
                .thenAnswer(call -> {
                    driver.setStatus(DriverStatus.IN_RIDE);
                    return 1;
                });
        when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
    }

    private void givenNoAvailableDriver() {
        when(driverRepository.findByStatusAndActiveTrue(DriverStatus.AVAILABLE))
                .thenReturn(List.of());
    }

    /** Candidato existe na lista, mas outra instancia o reserva primeiro. */
    private void givenDriverStolenByAnotherInstance(Driver driver) {
        when(driverRepository.findByStatusAndActiveTrue(DriverStatus.AVAILABLE))
                .thenReturn(List.of(driver));
        when(driverRepository.claimDriver(driver.getId(), DriverStatus.AVAILABLE, DriverStatus.IN_RIDE))
                .thenReturn(0);
    }

    private Driver availableDriver() {
        return Driver.builder()
                .id(UUID.randomUUID())
                .name("Maria Souza")
                .vehiclePlate("ABC-1234")
                .email("maria@exemplo.com")
                .password("hash")
                .status(DriverStatus.AVAILABLE)
                .active(true)
                .build();
    }

    private RideRequestDTO requestDto(UUID passengerIdInBody) {
        return RideRequestDTO.builder()
                .passengerId(passengerIdInBody)
                .origin(LocationDTO.builder()
                        .latitude(-19.2012).longitude(-46.2231)
                        .address("Predio de Aulas - UFV").build())
                .destination(LocationDTO.builder()
                        .latitude(-19.2045).longitude(-46.2290)
                        .address("Restaurante Universitario - UFV").build())
                .build();
    }

    /** Deixa a corrida recem-salva visivel para a etapa de atribuicao. */
    private void makeSavedRideVisibleForUpdate() {
        when(rideRepository.save(any(Ride.class))).thenAnswer(call -> {
            Ride saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            when(rideRepository.findByIdForUpdate(saved.getId())).thenReturn(Optional.of(saved));
            return saved;
        });
    }

    // ---------- solicitacao ----------

    @Test
    @DisplayName("Com motorista livre, a corrida e atribuida e vai para MATCH")
    void shouldMatchRideWhenDriverAvailable() {
        makeSavedRideVisibleForUpdate();
        Driver driver = availableDriver();
        givenAvailableDriver(driver);

        RideResponseDTO response = service.requestRide(requestDto(null), passengerCaller);

        assertThat(response.status()).isEqualTo(RideStatus.MATCH);
        assertThat(response.driverId()).isEqualTo(driver.getId());
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.IN_RIDE);
        assertThat(response.awaitingDelegation()).isFalse();
    }

    @Test
    @DisplayName("A corrida nasce com preco e ETA estimados, nunca nulos")
    void shouldEstimatePriceAndEtaOnCreation() {
        makeSavedRideVisibleForUpdate();
        givenNoAvailableDriver();

        RideResponseDTO response = service.requestRide(requestDto(null), passengerCaller);

        assertThat(response.price()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(response.etaSeconds()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("Em overflow, a corrida fica em REQUEST marcada para delegacao")
    void shouldKeepRideInPoolWhenOverflowed() {
        makeSavedRideVisibleForUpdate();
        givenOverflow(OverflowReason.NO_AVAILABLE_DRIVERS);

        RideResponseDTO response = service.requestRide(requestDto(null), passengerCaller);

        // decisao do projeto: nao vira DELEGATED nem e rejeitada. Se um motorista
        // ficar livre antes de o Core assumir, ainda e atendida localmente.
        assertThat(response.status()).isEqualTo(RideStatus.REQUEST);
        assertThat(response.awaitingDelegation()).isTrue();
        assertThat(response.driverId()).isNull();
    }

    @Test
    @DisplayName("Em overflow, um evento e publicado para a Semana 3 plugar o Core")
    void shouldPublishOverflowEvent() {
        makeSavedRideVisibleForUpdate();
        givenOverflow(OverflowReason.PENDING_QUEUE_ABOVE_THRESHOLD);

        service.requestRide(requestDto(null), passengerCaller);

        verify(eventPublisher).publishEvent(any(RideOverflowedEvent.class));
    }

    @Test
    @DisplayName("Se outra instancia reserva o motorista primeiro, a corrida aguarda no pool")
    void shouldLeaveRideInPoolWhenDriverIsStolen() {
        makeSavedRideVisibleForUpdate();
        givenDriverStolenByAnotherInstance(availableDriver());

        RideResponseDTO response = service.requestRide(requestDto(null), passengerCaller);

        // claimDriver devolveu 0: a disputa foi perdida e nada foi atribuido
        assertThat(response.status()).isEqualTo(RideStatus.REQUEST);
        assertThat(response.driverId()).isNull();
    }

    @Test
    @DisplayName("Sem overflow e sem motorista livre, a corrida aguarda no pool")
    void shouldLeaveRideInPoolWhenNoDriverAvailable() {
        makeSavedRideVisibleForUpdate();
        givenNoAvailableDriver();

        RideResponseDTO response = service.requestRide(requestDto(null), passengerCaller);

        assertThat(response.status()).isEqualTo(RideStatus.REQUEST);
        assertThat(response.driverId()).isNull();
    }

    @Test
    @DisplayName("passengerId do corpo divergindo do token devolve 403")
    void shouldRejectPassengerIdThatDoesNotMatchToken() {
        UUID outroPassageiro = UUID.randomUUID();

        // honrar o id do corpo permitiria pedir corrida em nome de outra pessoa
        assertThatThrownBy(() -> service.requestRide(requestDto(outroPassageiro), passengerCaller))
                .isInstanceOf(IdentityMismatchException.class);
    }

    @Test
    @DisplayName("passengerId do corpo igual ao do token e aceito")
    void shouldAcceptMatchingPassengerIdInBody() {
        makeSavedRideVisibleForUpdate();
        givenNoAvailableDriver();

        RideResponseDTO response = service.requestRide(requestDto(passenger.getId()), passengerCaller);

        assertThat(response.passengerId()).isEqualTo(passenger.getId());
    }

    // ---------- leitura e autorizacao ----------

    private Ride existingRide(RideStatus status, Driver driver) {
        Ride ride = Ride.builder()
                .id(UUID.randomUUID())
                .passenger(passenger)
                .driver(driver)
                .status(status)
                .originLatitude(-19.2012).originLongitude(-46.2231).originAddress("A")
                .destinationLatitude(-19.2045).destinationLongitude(-46.2290).destinationAddress("B")
                .price(new BigDecimal("18.50"))
                .etaSeconds(360)
                .logicalTimestamp(5L)
                .awaitingDelegation(false)
                .build();
        when(rideRepository.findById(ride.getId())).thenReturn(Optional.of(ride));
        when(rideRepository.findByIdForUpdate(ride.getId())).thenReturn(Optional.of(ride));
        return ride;
    }

    @Test
    @DisplayName("O passageiro dono da corrida pode consulta-la")
    void ownerPassengerShouldReadRide() {
        Ride ride = existingRide(RideStatus.REQUEST, null);

        assertThat(service.getRideForCaller(ride.getId(), passengerCaller).rideId()).isEqualTo(ride.getId());
    }

    @Test
    @DisplayName("O motorista atribuido pode consultar a corrida")
    void assignedDriverShouldReadRide() {
        Driver driver = availableDriver();
        Ride ride = existingRide(RideStatus.MATCH, driver);
        AuthenticatedUser driverCaller =
                new AuthenticatedUser(driver.getId(), driver.getEmail(), "ROLE_DRIVER");

        assertThat(service.getRideForCaller(ride.getId(), driverCaller).rideId()).isEqualTo(ride.getId());
    }

    @Test
    @DisplayName("Outro passageiro nao pode consultar a corrida")
    void thirdPartyShouldNotReadRide() {
        Ride ride = existingRide(RideStatus.REQUEST, null);
        AuthenticatedUser intruso =
                new AuthenticatedUser(UUID.randomUUID(), "outro@exemplo.com", "ROLE_PASSENGER");

        assertThatThrownBy(() -> service.getRideForCaller(ride.getId(), intruso))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("Administrador pode consultar qualquer corrida")
    void adminShouldReadAnyRide() {
        Ride ride = existingRide(RideStatus.REQUEST, null);
        AuthenticatedUser admin =
                new AuthenticatedUser(UUID.randomUUID(), "admin@ridefleet.local", "ROLE_ADMIN");

        assertThat(service.getRideForCaller(ride.getId(), admin)).isNotNull();
    }

    // ---------- acoes do motorista ----------

    private AuthenticatedUser callerFor(Driver driver) {
        return new AuthenticatedUser(driver.getId(), driver.getEmail(), "ROLE_DRIVER");
    }

    @Test
    @DisplayName("Motorista atribuido aceita a corrida, que vai para CONFIRM")
    void assignedDriverShouldAcceptRide() {
        Driver driver = availableDriver();
        Ride ride = existingRide(RideStatus.MATCH, driver);

        RideResponseDTO response = service.acceptByDriver(ride.getId(), callerFor(driver));

        assertThat(response.status()).isEqualTo(RideStatus.CONFIRM);
    }

    @Test
    @DisplayName("Motorista que nao e o atribuido nao pode aceitar")
    void otherDriverShouldNotAcceptRide() {
        Driver assigned = availableDriver();
        Ride ride = existingRide(RideStatus.MATCH, assigned);
        Driver intruso = availableDriver();

        assertThatThrownBy(() -> service.acceptByDriver(ride.getId(), callerFor(intruso)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("Aceitar corrida que nao esta em MATCH e recusado")
    void shouldRejectAcceptWhenRideIsNotInMatch() {
        Driver driver = availableDriver();
        Ride ride = existingRide(RideStatus.IN_TRANSIT, driver);

        assertThatThrownBy(() -> service.acceptByDriver(ride.getId(), callerFor(driver)))
                .isInstanceOf(InvalidRideTransitionException.class);
    }

    @Test
    @DisplayName("Recusar devolve a corrida ao pool e libera o motorista")
    void declineShouldReturnRideToPoolAndFreeDriver() {
        Driver driver = availableDriver();
        driver.setStatus(DriverStatus.IN_RIDE);
        Ride ride = existingRide(RideStatus.MATCH, driver);

        RideResponseDTO response = service.declineByDriver(ride.getId(), callerFor(driver));

        assertThat(response.status()).isEqualTo(RideStatus.REQUEST);
        assertThat(response.driverId()).isNull();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Iniciar a corrida a leva para IN_TRANSIT")
    void shouldStartRide() {
        Driver driver = availableDriver();
        Ride ride = existingRide(RideStatus.CONFIRM, driver);

        assertThat(service.startByDriver(ride.getId(), callerFor(driver)).status())
                .isEqualTo(RideStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("Concluir a corrida libera o motorista de volta para AVAILABLE")
    void completeShouldFreeDriver() {
        Driver driver = availableDriver();
        driver.setStatus(DriverStatus.IN_RIDE);
        Ride ride = existingRide(RideStatus.IN_TRANSIT, driver);

        RideResponseDTO response = service.completeByDriver(ride.getId(), callerFor(driver));

        assertThat(response.status()).isEqualTo(RideStatus.COMPLETE);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
    }

    // ---------- cancelamento ----------

    @Test
    @DisplayName("Passageiro dono cancela a corrida e o motorista e liberado")
    void ownerShouldCancelRide() {
        Driver driver = availableDriver();
        driver.setStatus(DriverStatus.IN_RIDE);
        Ride ride = existingRide(RideStatus.CONFIRM, driver);

        RideResponseDTO response = service.cancelRide(ride.getId(), "Mudei de ideia", passengerCaller);

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Cancelar corrida ja concluida e recusado")
    void shouldRejectCancellingCompletedRide() {
        Ride ride = existingRide(RideStatus.COMPLETE, availableDriver());

        assertThatThrownBy(() -> service.cancelRide(ride.getId(), null, passengerCaller))
                .isInstanceOf(InvalidRideTransitionException.class);
    }

    // ---------- relogio logico ----------

    @Test
    @DisplayName("Cada transicao estampa um relogio logico estritamente crescente")
    void eachTransitionShouldStampIncreasingLogicalTimestamp() {
        Driver driver = availableDriver();
        Ride ride = existingRide(RideStatus.MATCH, driver);
        long afterMatch = ride.getLogicalTimestamp();

        service.acceptByDriver(ride.getId(), callerFor(driver));
        long afterConfirm = ride.getLogicalTimestamp();

        service.startByDriver(ride.getId(), callerFor(driver));
        long afterTransit = ride.getLogicalTimestamp();

        service.completeByDriver(ride.getId(), callerFor(driver));
        long afterComplete = ride.getLogicalTimestamp();

        assertThat(afterConfirm).isGreaterThan(afterMatch);
        assertThat(afterTransit).isGreaterThan(afterConfirm);
        assertThat(afterComplete).isGreaterThan(afterTransit);
    }

    @Test
    @DisplayName("O relogio sincroniza com o timestamp gravado na corrida")
    void shouldSyncClockWithStoredRideTimestamp() {
        Driver driver = availableDriver();
        Ride ride = existingRide(RideStatus.MATCH, driver);
        ride.setLogicalTimestamp(500L); // corrida escrita por outra instancia

        service.acceptByDriver(ride.getId(), callerFor(driver));

        // ler a linha compartilhada e, causalmente, receber uma mensagem:
        // max(local, recebido) + 1 mantem a ordem entre instancias
        assertThat(ride.getLogicalTimestamp()).isEqualTo(501L);
        assertThat(lamportClock.current()).isEqualTo(501L);
    }
}
