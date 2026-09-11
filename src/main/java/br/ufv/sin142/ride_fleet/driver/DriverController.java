package br.ufv.sin142.ride_fleet.driver;

import br.ufv.sin142.ride_fleet.auth.DriverRegisterDTO;
import br.ufv.sin142.ride_fleet.auth.DriverResponseDTO;
import br.ufv.sin142.ride_fleet.driver.dto.DriverAdminUpdateDTO;
import br.ufv.sin142.ride_fleet.driver.dto.DriverLocationDTO;
import br.ufv.sin142.ride_fleet.driver.dto.DriverSelfUpdateDTO;
import br.ufv.sin142.ride_fleet.driver.dto.DriverStatusUpdateDTO;
import br.ufv.sin142.ride_fleet.ride.RideService;
import br.ufv.sin142.ride_fleet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de motorista: autosservico do proprio motorista e CRUD do administrador.
 *
 * As rotas /me e /{id} convivem sem conflito: o PathPattern do Spring prefere o
 * segmento literal ao variavel. A ordem das regras no SecurityConfig, por outro
 * lado, importa - ver comentario la.
 *
 * O "create" do CRUD tambem existe em POST /api/v1/auth/driver/register; ambos
 * passam pelo mesmo DriverService.createDriver.
 */
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService driverService;
    private final RideService rideService;

    public DriverController(DriverService driverService, RideService rideService) {
        this.driverService = driverService;
        this.rideService = rideService;
    }

    // ---------------- autosservico ----------------

    @GetMapping("/me")
    public ResponseEntity<DriverResponseDTO> getMe(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(toDto(driverService.getById(caller.id())));
    }

    @PatchMapping("/me")
    public ResponseEntity<DriverResponseDTO> updateMe(
            @Valid @RequestBody DriverSelfUpdateDTO dto,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        return ResponseEntity.ok(toDto(driverService.updateSelf(caller.id(), dto)));
    }

    /**
     * Entrar e sair de servico.
     *
     * Ao ficar AVAILABLE o servico tenta drenar o pool de pendentes: este e o
     * segundo gatilho de atribuicao, alem da criacao da corrida. E o que faz uma
     * corrida marcada para delegacao ainda ser atendida localmente.
     */
    @PatchMapping("/me/status")
    public ResponseEntity<DriverResponseDTO> updateMyStatus(
            @Valid @RequestBody DriverStatusUpdateDTO dto,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        Driver driver = driverService.changeStatus(
                driverService.getById(caller.id()), dto.getStatus(), false);

        if (dto.getStatus() == DriverStatus.AVAILABLE) {
            rideService.drainPendingPool();
        }
        return ResponseEntity.ok(toDto(driverService.getById(driver.getId())));
    }

    @PatchMapping("/me/location")
    public ResponseEntity<DriverResponseDTO> updateMyLocation(
            @Valid @RequestBody DriverLocationDTO dto,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        Driver driver = driverService.updateLocation(
                driverService.getById(caller.id()), dto.getLatitude(), dto.getLongitude());
        return ResponseEntity.ok(toDto(driver));
    }

    @GetMapping("/me/rides")
    public ResponseEntity<?> getMyRides(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(rideService.listForDriver(caller.id()));
    }

    // ---------------- administracao ----------------

    @GetMapping
    public ResponseEntity<List<DriverResponseDTO>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Driver> page = driverService.list(includeInactive, pageable);
        return ResponseEntity.ok(page.map(this::toDto).getContent());
    }

    @PostMapping
    public ResponseEntity<DriverResponseDTO> create(@Valid @RequestBody DriverRegisterDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDto(driverService.createDriver(dto)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DriverResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(toDto(driverService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DriverResponseDTO> update(
            @PathVariable UUID id, @Valid @RequestBody DriverAdminUpdateDTO dto) {

        return ResponseEntity.ok(toDto(driverService.adminUpdate(id, dto)));
    }

    /** Forca status pelo administrador, sujeito as mesmas guardas do autosservico. */
    @PutMapping("/{id}/status")
    public ResponseEntity<DriverResponseDTO> forceStatus(
            @PathVariable UUID id, @Valid @RequestBody DriverStatusUpdateDTO dto) {

        Driver driver = driverService.changeStatus(driverService.getById(id), dto.getStatus(), true);
        return ResponseEntity.ok(toDto(driver));
    }

    /** Exclusao logica: a linha permanece, preservando o historico de corridas. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        driverService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    private DriverResponseDTO toDto(Driver driver) {
        return DriverResponseDTO.builder()
                .id(driver.getId())
                .name(driver.getName())
                .vehiclePlate(driver.getVehiclePlate())
                .email(driver.getEmail())
                .status(driver.getStatus())
                .currentLatitude(driver.getCurrentLatitude())
                .currentLongitude(driver.getCurrentLongitude())
                .active(driver.getActive())
                .build();
    }
}
