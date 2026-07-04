package br.ufv.sin142.ride_fleet.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/passenger/register")
    public ResponseEntity<PassengerResponseDTO> registerPassenger(@Valid @RequestBody PassengerRegisterDTO dto) {
        PassengerResponseDTO response = authService.registerPassenger(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/driver/register")
    public ResponseEntity<DriverResponseDTO> registerDriver(@Valid @RequestBody DriverRegisterDTO dto) {
        DriverResponseDTO response = authService.registerDriver(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/passenger/login")
    public ResponseEntity<LoginResponseDTO> loginPassenger(@Valid @RequestBody LoginRequestDTO dto) {
        LoginResponseDTO response = authService.loginPassenger(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/driver/login")
    public ResponseEntity<LoginResponseDTO> loginDriver(@Valid @RequestBody LoginRequestDTO dto) {
        LoginResponseDTO response = authService.loginDriver(dto);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        String message = ex.getMessage();
        errorResponse.put("message", message);

        if ("Credenciais invalidas.".equals(message)) {
            errorResponse.put("error", "Unauthorized");
            return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
        } else {
            errorResponse.put("error", "Bad Request");
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }
}
