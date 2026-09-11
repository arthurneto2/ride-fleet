package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.ride.dto.CancelRideDTO;
import br.ufv.sin142.ride_fleet.ride.dto.RideRequestDTO;
import br.ufv.sin142.ride_fleet.ride.dto.RideResponseDTO;
import br.ufv.sin142.ride_fleet.security.AuthenticatedUser;
import br.ufv.sin142.ride_fleet.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de corrida para o front-end.
 *
 * As controllers ficam finas de proposito: autorizacao sobre o recurso e regra de
 * transicao vivem no RideService, porque as mesmas regras valem para varios
 * endpoints e duplica-las aqui e onde um furo apareceria.
 */
@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    /**
     * 202 Accepted e intencional e esta no contrato: a corrida foi aceita para
     * processamento, nao necessariamente ja atribuida - ela pode estar esperando
     * motorista ou aguardando delegacao.
     */
    @PostMapping("/request")
    public ResponseEntity<RideResponseDTO> requestRide(
            @Valid @RequestBody RideRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        RideResponseDTO response = rideService.requestRide(dto, caller);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Historico do proprio usuario. Rota literal, declarada antes de /{id}. */
    @GetMapping("/me")
    public ResponseEntity<List<RideResponseDTO>> listMyRides(
            @AuthenticationPrincipal AuthenticatedUser caller) {

        List<RideResponseDTO> rides = JwtService.ROLE_DRIVER.equals(caller.role())
                ? rideService.listForDriver(caller.id())
                : rideService.listForPassenger(caller.id());

        return ResponseEntity.ok(rides);
    }

    /** Pool local de corridas pendentes, em ordem de chegada. */
    @GetMapping("/pending")
    public ResponseEntity<List<RideResponseDTO>> listPendingPool() {
        return ResponseEntity.ok(rideService.listPendingPool());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RideResponseDTO> getRide(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        return ResponseEntity.ok(rideService.getRideForCaller(id, caller));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<RideResponseDTO> accept(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        return ResponseEntity.ok(rideService.acceptByDriver(id, caller));
    }

    /** Recusa: a corrida volta ao pool local e o motorista e liberado. */
    @PostMapping("/{id}/decline")
    public ResponseEntity<RideResponseDTO> decline(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        return ResponseEntity.ok(rideService.declineByDriver(id, caller));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<RideResponseDTO> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        return ResponseEntity.ok(rideService.startByDriver(id, caller));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<RideResponseDTO> complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        return ResponseEntity.ok(rideService.completeByDriver(id, caller));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<RideResponseDTO> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid CancelRideDTO dto,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        String reason = dto == null ? null : dto.getReason();
        return ResponseEntity.ok(rideService.cancelRide(id, reason, caller));
    }
}
