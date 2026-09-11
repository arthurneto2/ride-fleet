package br.ufv.sin142.ride_fleet.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints publicos de cadastro e autenticacao.
 *
 * O @ExceptionHandler local foi removido: ele decidia o status HTTP comparando a
 * string da mensagem da excecao, de modo que renomear uma mensagem mudava
 * silenciosamente o codigo de resposta. Agora InvalidCredentialsException mapeia
 * para 401 e IllegalArgumentException para 400 no advice global - que e o codigo
 * documentado em api_spec.md secao 3.1 para e-mail ou placa ja cadastrados.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/passenger/register")
    public ResponseEntity<PassengerResponseDTO> registerPassenger(@Valid @RequestBody PassengerRegisterDTO dto) {
        return new ResponseEntity<>(authService.registerPassenger(dto), HttpStatus.CREATED);
    }

    @PostMapping("/driver/register")
    public ResponseEntity<DriverResponseDTO> registerDriver(@Valid @RequestBody DriverRegisterDTO dto) {
        return new ResponseEntity<>(authService.registerDriver(dto), HttpStatus.CREATED);
    }

    @PostMapping("/passenger/login")
    public ResponseEntity<LoginResponseDTO> loginPassenger(@Valid @RequestBody LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.loginPassenger(dto));
    }

    @PostMapping("/driver/login")
    public ResponseEntity<LoginResponseDTO> loginDriver(@Valid @RequestBody LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.loginDriver(dto));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<LoginResponseDTO> loginAdmin(@Valid @RequestBody LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.loginAdmin(dto));
    }
}
