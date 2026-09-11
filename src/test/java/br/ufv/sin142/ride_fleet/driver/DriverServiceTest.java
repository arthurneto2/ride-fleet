package br.ufv.sin142.ride_fleet.driver;

import br.ufv.sin142.ride_fleet.auth.DriverRegisterDTO;
import br.ufv.sin142.ride_fleet.ride.RideRepository;
import br.ufv.sin142.ride_fleet.shared.exception.ConflictException;
import br.ufv.sin142.ride_fleet.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do servico de motoristas.
 *
 * O status do motorista e uma segunda maquina de estados, menor que a da
 * corrida mas igualmente critica: IN_RIDE e estado derivado, de responsabilidade
 * do matcher. Se alguem puder defini-lo ou sair dele a mao, o mecanismo de lock
 * da atribuicao deixa de valer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private RideRepository rideRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DriverService service;

    @BeforeEach
    void setUp() {
        service = new DriverService(driverRepository, rideRepository, passwordEncoder);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-bcrypt");
        when(driverRepository.save(any(Driver.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Driver driverWith(DriverStatus status) {
        return Driver.builder()
                .id(UUID.randomUUID())
                .name("Maria Souza")
                .vehiclePlate("ABC-1234")
                .email("maria@exemplo.com")
                .password("hash-bcrypt")
                .status(status)
                .active(true)
                .build();
    }

    // ---------- cadastro ----------

    @Test
    @DisplayName("Cadastro inicia o motorista offline e ativo")
    void shouldCreateDriverOfflineAndActive() {
        DriverRegisterDTO dto = DriverRegisterDTO.builder()
                .name("Maria Souza")
                .vehiclePlate("ABC-1234")
                .email("maria@exemplo.com")
                .password("senhaSegura456")
                .build();

        Driver created = service.createDriver(dto);

        assertThat(created.getStatus()).isEqualTo(DriverStatus.OFFLINE);
        assertThat(created.getActive()).isTrue();
        assertThat(created.getPassword()).isEqualTo("hash-bcrypt");
    }

    @Test
    @DisplayName("Cadastro com e-mail duplicado e recusado")
    void shouldRejectDuplicateEmail() {
        when(driverRepository.existsByEmail("maria@exemplo.com")).thenReturn(true);

        DriverRegisterDTO dto = DriverRegisterDTO.builder()
                .name("Maria").vehiclePlate("XYZ-9999")
                .email("maria@exemplo.com").password("senha123").build();

        assertThatThrownBy(() -> service.createDriver(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email ja cadastrado");
    }

    @Test
    @DisplayName("Cadastro com placa duplicada e recusado com 400, nao com erro de banco")
    void shouldRejectDuplicatePlate() {
        // hoje a unicidade da placa so existe como constraint do banco, o que
        // produz 500 em vez do 400 documentado na spec
        when(driverRepository.existsByVehiclePlate("ABC-1234")).thenReturn(true);

        DriverRegisterDTO dto = DriverRegisterDTO.builder()
                .name("Maria").vehiclePlate("ABC-1234")
                .email("nova@exemplo.com").password("senha123").build();

        assertThatThrownBy(() -> service.createDriver(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Placa ja cadastrada");
    }

    @Test
    @DisplayName("Placa e normalizada para maiusculas, evitando duplicatas disfarcadas")
    void shouldNormalizePlate() {
        DriverRegisterDTO dto = DriverRegisterDTO.builder()
                .name("Maria").vehiclePlate("  abc-1234 ")
                .email("maria@exemplo.com").password("senha123").build();

        Driver created = service.createDriver(dto);

        assertThat(created.getVehiclePlate()).isEqualTo("ABC-1234");
        verify(driverRepository).existsByVehiclePlate("ABC-1234");
    }

    // ---------- mudanca de status ----------

    @Test
    @DisplayName("Motorista offline pode ficar disponivel")
    void shouldAllowOfflineToAvailable() {
        Driver driver = driverWith(DriverStatus.OFFLINE);

        service.changeStatus(driver, DriverStatus.AVAILABLE, false);

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Motorista disponivel pode sair de servico")
    void shouldAllowAvailableToOffline() {
        Driver driver = driverWith(DriverStatus.AVAILABLE);

        service.changeStatus(driver, DriverStatus.AVAILABLE, false);
        service.changeStatus(driver, DriverStatus.OFFLINE, false);

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.OFFLINE);
    }

    @Test
    @DisplayName("Ninguem pode se declarar IN_RIDE manualmente")
    void shouldRejectManualInRide() {
        Driver driver = driverWith(DriverStatus.AVAILABLE);

        // IN_RIDE e derivado: definido a mao cria motorista ocupado com nada,
        // permanentemente inatribuivel
        assertThatThrownBy(() -> service.changeStatus(driver, DriverStatus.IN_RIDE, false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.changeStatus(driver, DriverStatus.IN_RIDE, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Motorista com corrida ativa nao pode sair de IN_RIDE")
    void shouldRejectLeavingInRideWhileRideIsActive() {
        Driver driver = driverWith(DriverStatus.IN_RIDE);
        when(rideRepository.existsByDriverIdAndStatusIn(any(UUID.class), anyCollection())).thenReturn(true);

        // liberar um motorista ocupado permitiria ao matcher dar-lhe uma segunda
        // corrida, que e exatamente a corrida dupla que o lock existe para evitar
        assertThatThrownBy(() -> service.changeStatus(driver, DriverStatus.AVAILABLE, false))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> service.changeStatus(driver, DriverStatus.OFFLINE, true))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Motorista preso em IN_RIDE sem corrida ativa pode ser liberado")
    void shouldAllowLeavingInRideWhenNoActiveRide() {
        Driver driver = driverWith(DriverStatus.IN_RIDE);
        when(rideRepository.existsByDriverIdAndStatusIn(any(UUID.class), anyCollection())).thenReturn(false);

        service.changeStatus(driver, DriverStatus.AVAILABLE, true);

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
    }

    // ---------- exclusao logica ----------

    @Test
    @DisplayName("Exclusao marca o motorista como inativo e offline, sem apagar a linha")
    void shouldSoftDelete() {
        Driver driver = driverWith(DriverStatus.AVAILABLE);
        when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(rideRepository.existsByDriverIdAndStatusIn(any(UUID.class), anyCollection())).thenReturn(false);

        service.softDelete(driver.getId());

        assertThat(driver.getActive()).isFalse();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.OFFLINE);
        verify(driverRepository, never()).delete(any(Driver.class));
    }

    @Test
    @DisplayName("Excluir motorista com corrida em andamento e recusado")
    void shouldRejectDeletingDriverWithActiveRide() {
        Driver driver = driverWith(DriverStatus.IN_RIDE);
        when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
        when(rideRepository.existsByDriverIdAndStatusIn(any(UUID.class), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(driver.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("corrida");
    }

    @Test
    @DisplayName("Excluir motorista ja inativo e idempotente")
    void shouldBeIdempotentWhenAlreadyInactive() {
        Driver driver = driverWith(DriverStatus.OFFLINE);
        driver.setActive(false);
        when(driverRepository.findById(driver.getId())).thenReturn(Optional.of(driver));

        service.softDelete(driver.getId());

        assertThat(driver.getActive()).isFalse();
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("Excluir motorista inexistente devolve 404")
    void shouldRejectDeletingUnknownDriver() {
        UUID unknown = UUID.randomUUID();
        when(driverRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- localizacao ----------

    @Test
    @DisplayName("Atualizacao de localizacao grava as coordenadas")
    void shouldUpdateLocation() {
        Driver driver = driverWith(DriverStatus.AVAILABLE);

        service.updateLocation(driver, -19.2012, -46.2231);

        assertThat(driver.getCurrentLatitude()).isEqualTo(-19.2012);
        assertThat(driver.getCurrentLongitude()).isEqualTo(-46.2231);
    }
}
