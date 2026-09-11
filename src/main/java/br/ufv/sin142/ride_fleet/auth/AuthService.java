package br.ufv.sin142.ride_fleet.auth;

import br.ufv.sin142.ride_fleet.admin.Admin;
import br.ufv.sin142.ride_fleet.admin.AdminRepository;
import br.ufv.sin142.ride_fleet.driver.Driver;
import br.ufv.sin142.ride_fleet.driver.DriverRepository;
import br.ufv.sin142.ride_fleet.driver.DriverService;
import br.ufv.sin142.ride_fleet.passenger.Passenger;
import br.ufv.sin142.ride_fleet.passenger.PassengerRepository;
import br.ufv.sin142.ride_fleet.security.JwtService;
import br.ufv.sin142.ride_fleet.shared.exception.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final PassengerRepository passengerRepository;
    private final DriverRepository driverRepository;
    private final AdminRepository adminRepository;
    private final DriverService driverService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(PassengerRepository passengerRepository,
                       DriverRepository driverRepository,
                       AdminRepository adminRepository,
                       DriverService driverService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.passengerRepository = passengerRepository;
        this.driverRepository = driverRepository;
        this.adminRepository = adminRepository;
        this.driverService = driverService;
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

    /**
     * Delega ao DriverService para que autocadastro e criacao via administrador
     * passem pelo mesmo caminho - assim a validacao de unicidade de e-mail e placa
     * nao pode divergir entre os dois.
     */
    @Transactional
    public DriverResponseDTO registerDriver(DriverRegisterDTO dto) {
        Driver saved = driverService.createDriver(dto);
        return DriverResponseDTO.builder()
                .id(saved.getId())
                .name(saved.getName())
                .vehiclePlate(saved.getVehiclePlate())
                .email(saved.getEmail())
                .status(saved.getStatus())
                .currentLatitude(saved.getCurrentLatitude())
                .currentLongitude(saved.getCurrentLongitude())
                .active(saved.getActive())
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO loginPassenger(LoginRequestDTO dto) {
        Passenger passenger = passengerRepository.findByEmail(dto.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(dto.getPassword(), passenger.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return LoginResponseDTO.builder()
                .token(jwtService.generatePassengerToken(passenger.getId(), passenger.getEmail()))
                .role(JwtService.ROLE_PASSENGER)
                .id(passenger.getId())
                .name(passenger.getName())
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO loginDriver(LoginRequestDTO dto) {
        Driver driver = driverRepository.findByEmail(dto.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        // Motorista excluido logicamente nao entra. A mensagem e a mesma de senha
        // errada de proposito: dizer "conta desativada" entregaria a existencia da
        // conta a quem esta sondando.
        if (Boolean.FALSE.equals(driver.getActive())
                || !passwordEncoder.matches(dto.getPassword(), driver.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return LoginResponseDTO.builder()
                .token(jwtService.generateDriverToken(driver.getId(), driver.getEmail()))
                .role(JwtService.ROLE_DRIVER)
                .id(driver.getId())
                .name(driver.getName())
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO loginAdmin(LoginRequestDTO dto) {
        Admin admin = adminRepository.findByEmail(dto.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(dto.getPassword(), admin.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return LoginResponseDTO.builder()
                .token(jwtService.generateAdminToken(admin.getId(), admin.getEmail()))
                .role(JwtService.ROLE_ADMIN)
                .id(admin.getId())
                .name(admin.getName())
                .build();
    }
}
