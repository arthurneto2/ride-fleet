package br.ufv.sin142.ride_fleet.auth;

import br.ufv.sin142.ride_fleet.driver.Driver;
import br.ufv.sin142.ride_fleet.driver.DriverRepository;
import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import br.ufv.sin142.ride_fleet.passenger.Passenger;
import br.ufv.sin142.ride_fleet.passenger.PassengerRepository;
import br.ufv.sin142.ride_fleet.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final PassengerRepository passengerRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(PassengerRepository passengerRepository,
                       DriverRepository driverRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.passengerRepository = passengerRepository;
        this.driverRepository = driverRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public PassengerResponseDTO registerPassenger(PassengerRegisterDTO dto) {
        if (passengerRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email ja cadastrado para outro passageiro.");
        }

        Passenger passenger = Passenger.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();

        Passenger saved = passengerRepository.save(passenger);
        return PassengerResponseDTO.builder()
                .id(saved.getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .build();
    }

    @Transactional
    public DriverResponseDTO registerDriver(DriverRegisterDTO dto) {
        if (driverRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email ja cadastrado para outro motorista.");
        }

        Driver driver = Driver.builder()
                .name(dto.getName())
                .vehiclePlate(dto.getVehiclePlate())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .status(DriverStatus.OFFLINE) // Status inicial offline
                .currentLatitude(0.0)
                .currentLongitude(0.0)
                .build();

        Driver saved = driverRepository.save(driver);
        return DriverResponseDTO.builder()
                .id(saved.getId())
                .name(saved.getName())
                .vehiclePlate(saved.getVehiclePlate())
                .email(saved.getEmail())
                .status(saved.getStatus())
                .currentLatitude(saved.getCurrentLatitude())
                .currentLongitude(saved.getCurrentLongitude())
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO loginPassenger(LoginRequestDTO dto) {
        Passenger passenger = passengerRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenciais invalidas."));

        if (!passwordEncoder.matches(dto.getPassword(), passenger.getPassword())) {
            throw new IllegalArgumentException("Credenciais invalidas.");
        }

        String token = jwtService.generatePassengerToken(passenger.getId(), passenger.getEmail());

        return LoginResponseDTO.builder()
                .token(token)
                .role("ROLE_PASSENGER")
                .id(passenger.getId())
                .name(passenger.getName())
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO loginDriver(LoginRequestDTO dto) {
        Driver driver = driverRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Credenciais invalidas."));

        if (!passwordEncoder.matches(dto.getPassword(), driver.getPassword())) {
            throw new IllegalArgumentException("Credenciais invalidas.");
        }

        String token = jwtService.generateDriverToken(driver.getId(), driver.getEmail());

        return LoginResponseDTO.builder()
                .token(token)
                .role("ROLE_DRIVER")
                .id(driver.getId())
                .name(driver.getName())
                .build();
    }
}
