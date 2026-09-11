package br.ufv.sin142.ride_fleet.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cria o administrador inicial, sem o qual ninguem consegue entrar da primeira vez.
 */
@Component
@ConditionalOnProperty(name = "ridefleet.admin.seed-enabled", havingValue = "true", matchIfMissing = true)
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminRepository adminRepository;
    private final AdminProperties properties;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(AdminRepository adminRepository,
                       AdminProperties properties,
                       PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            return;
        }

        boolean generated = properties.getPassword() == null || properties.getPassword().isBlank();
        String plainPassword = generated ? UUID.randomUUID().toString() : properties.getPassword();

        try {
            adminRepository.save(Admin.builder()
                    .name(properties.getName())
                    .email(properties.getEmail())
                    .password(passwordEncoder.encode(plainPassword))
                    .build());

            if (generated) {
                log.warn("Administrador inicial criado: {} / senha gerada: {} - altere imediatamente.",
                        properties.getEmail(), plainPassword);
            } else {
                log.warn("Administrador inicial criado: {} com senha definida por configuracao.",
                        properties.getEmail());
            }
        } catch (DataIntegrityViolationException e) {
            // Com duas instancias subindo juntas na Semana 2, ambas veem count() == 0
            // e ambas inserem; a restricao de unicidade de e-mail derruba a perdedora.
            // Deixar isso matar o processo produziria loop de restart no Compose.
            log.info("Administrador inicial ja foi criado por outra instancia.");
        }
    }
}
