package br.ufv.sin142.ride_fleet.support;

import br.ufv.sin142.ride_fleet.admin.Admin;
import br.ufv.sin142.ride_fleet.admin.AdminRepository;
import br.ufv.sin142.ride_fleet.driver.DriverRepository;
import br.ufv.sin142.ride_fleet.passenger.PassengerRepository;
import br.ufv.sin142.ride_fleet.ride.RideRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integracao.
 *
 * A limpeza comeca pelas corridas de proposito: rides.driver_id e chave
 * estrangeira, entao apagar motoristas antes das corridas viola a restricao. E
 * como @SpringBootTest compartilha o mesmo H2 entre classes, uma corrida deixada
 * por outra classe quebraria esta dependendo da ordem de execucao.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected RideRepository rideRepository;

    @Autowired
    protected DriverRepository driverRepository;

    @Autowired
    protected PassengerRepository passengerRepository;

    @Autowired
    protected AdminRepository adminRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected MockMvc mockMvc;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpIntegrationTest() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        rideRepository.deleteAll();
        driverRepository.deleteAll();
        passengerRepository.deleteAll();
        adminRepository.deleteAll();
    }

    /**
     * Cria um administrador direto no repositorio.
     *
     * Nao existe endpoint publico de cadastro de admin de proposito: o unico
     * caminho em producao e o seed inicial por configuracao.
     */
    protected Admin createAdmin(String email, String password) {
        return adminRepository.save(Admin.builder()
                .name("Administrador de Teste")
                .email(email)
                .password(passwordEncoder.encode(password))
                .build());
    }

    protected String loginAdmin(String email, String password) throws Exception {
        return tokenFrom("/api/v1/auth/admin/login", email, password);
    }

    protected JsonNode registerPassenger(String name, String email, String password) throws Exception {
        String body = """
                { "name": "%s", "email": "%s", "phone": "31999998888", "password": "%s" }
                """.formatted(name, email, password);

        String json = mockMvc.perform(post("/api/v1/auth/passenger/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json);
    }

    protected JsonNode registerDriver(String name, String plate, String email, String password) throws Exception {
        String body = """
                { "name": "%s", "vehiclePlate": "%s", "email": "%s", "password": "%s" }
                """.formatted(name, plate, email, password);

        String json = mockMvc.perform(post("/api/v1/auth/driver/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json);
    }

    protected String loginPassenger(String email, String password) throws Exception {
        return tokenFrom("/api/v1/auth/passenger/login", email, password);
    }

    protected String loginDriver(String email, String password) throws Exception {
        return tokenFrom("/api/v1/auth/driver/login", email, password);
    }

    private String tokenFrom(String url, String email, String password) throws Exception {
        String body = """
                { "email": "%s", "password": "%s" }
                """.formatted(email, password);

        String json = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json).get("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
