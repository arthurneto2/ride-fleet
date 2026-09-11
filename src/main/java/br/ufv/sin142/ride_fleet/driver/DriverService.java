package br.ufv.sin142.ride_fleet.driver;

import br.ufv.sin142.ride_fleet.auth.DriverRegisterDTO;
import br.ufv.sin142.ride_fleet.ride.RideRepository;
import br.ufv.sin142.ride_fleet.shared.exception.ConflictException;
import br.ufv.sin142.ride_fleet.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Regras de negocio de motorista: cadastro, status, localizacao e exclusao.
 *
 * O status do motorista e uma segunda maquina de estados, pequena mas critica.
 * Ela esta concentrada em {@link #changeStatus} para que tanto o autosservico do
 * motorista quanto o override do administrador passem pelas mesmas guardas.
 */
@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final RideRepository rideRepository;
    private final PasswordEncoder passwordEncoder;

    public DriverService(DriverRepository driverRepository,
                         RideRepository rideRepository,
                         PasswordEncoder passwordEncoder) {
        this.driverRepository = driverRepository;
        this.rideRepository = rideRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cria um motorista. Caminho unico, usado tanto pelo autocadastro em
     * /auth/driver/register quanto pela criacao via administrador, para que a
     * validacao de unicidade nao possa divergir entre os dois.
     */
    @Transactional
    public Driver createDriver(DriverRegisterDTO dto) {
        String email = dto.getEmail().trim().toLowerCase();
        String plate = normalizePlate(dto.getVehiclePlate());

        if (driverRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email ja cadastrado para outro motorista.");
        }
        if (driverRepository.existsByVehiclePlate(plate)) {
            throw new IllegalArgumentException("Placa ja cadastrada para outro motorista.");
        }

        Driver driver = Driver.builder()
                .name(dto.getName().trim())
                .vehiclePlate(plate)
                .email(email)
                .password(passwordEncoder.encode(dto.getPassword()))
                .status(DriverStatus.OFFLINE)
                .active(true)
                .currentLatitude(0.0)
                .currentLongitude(0.0)
                .build();

        return driverRepository.save(driver);
    }

    /**
     * Normaliza a placa para evitar duplicatas disfarcadas: sem isso,
     * "abc-1234" e "ABC-1234" criam dois motoristas distintos.
     */
    private String normalizePlate(String plate) {
        return plate.trim().toUpperCase();
    }

    /**
     * Altera o status do motorista respeitando duas invariantes.
     *
     * IN_RIDE nunca pode ser definido a mao: e estado derivado, de propriedade do
     * matcher. Definido manualmente cria um motorista ocupado com nada e
     * permanentemente inatribuivel.
     *
     * Sair de IN_RIDE com corrida ativa e bloqueado porque liberar um motorista
     * ocupado permite ao matcher dar-lhe uma segunda corrida - exatamente a
     * corrida dupla que todo o mecanismo de lock existe para evitar.
     *
     * @param byAdmin apenas para contexto de log; as guardas valem para os dois
     */
    @Transactional
    public Driver changeStatus(Driver driver, DriverStatus target, boolean byAdmin) {
        if (target == DriverStatus.IN_RIDE) {
            throw new IllegalArgumentException(
                    "O status IN_RIDE e definido pelo sistema ao atribuir uma corrida e nao pode ser informado.");
        }

        if (driver.getStatus() == DriverStatus.IN_RIDE && hasActiveRide(driver)) {
            throw new ConflictException(
                    "Motorista possui corrida em andamento e nao pode mudar de status agora.");
        }

        driver.setStatus(target);
        return driverRepository.save(driver);
    }

    @Transactional
    public Driver updateLocation(Driver driver, double latitude, double longitude) {
        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);
        return driverRepository.save(driver);
    }

    /**
     * Exclusao logica.
     *
     * rides.driver_id e chave estrangeira: apagar de verdade um motorista que ja
     * fez corrida viola a restricao, e apagar o historico seria pior, porque a
     * auditoria causal da Semana 7 depende dele.
     *
     * Idempotente: excluir um motorista ja inativo nao e erro.
     */
    @Transactional
    public void softDelete(UUID driverId) {
        Driver driver = getById(driverId);

        if (Boolean.FALSE.equals(driver.getActive())) {
            return;
        }
        if (hasActiveRide(driver)) {
            throw new ConflictException(
                    "Motorista possui corrida em andamento e nao pode ser excluido agora.");
        }

        driver.setActive(false);
        driver.setStatus(DriverStatus.OFFLINE);
        driverRepository.save(driver);
    }

    @Transactional(readOnly = true)
    public Driver getById(UUID driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Motorista", driverId));
    }

    @Transactional(readOnly = true)
    public Driver getByEmail(String email) {
        return driverRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Motorista", email));
    }

    @Transactional(readOnly = true)
    public Page<Driver> list(boolean includeInactive, Pageable pageable) {
        return includeInactive
                ? driverRepository.findAll(pageable)
                : driverRepository.findByActiveTrue(pageable);
    }

    private boolean hasActiveRide(Driver driver) {
        return rideRepository.existsByDriverIdAndStatusIn(driver.getId(), RideRepository.ACTIVE_STATUSES);
    }
}
