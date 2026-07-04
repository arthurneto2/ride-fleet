package br.ufv.sin142.ride_fleet.auth;

import br.ufv.sin142.ride_fleet.driver.DriverRepository;
import br.ufv.sin142.ride_fleet.passenger.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class SecurityAndAuthIntegrationTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private DriverRepository driverRepository;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        passengerRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    public void shouldRegisterPassengerSuccessfully() throws Exception {
        String passengerJson = """
                {
                  "name": "Joao do Teste",
                  "email": "joao.teste@gmail.com",
                  "phone": "31999998888",
                  "password": "senhaSegura123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/passenger/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passengerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Joao do Teste")))
                .andExpect(jsonPath("$.email", is("joao.teste@gmail.com")))
                .andExpect(jsonPath("$.phone", is("31999998888")))
                .andExpect(jsonPath("$.password").doesNotExist()); // Deveria estar ignorada pela serializacao
    }

    @Test
    public void shouldFailToRegisterPassengerWithDuplicateEmail() throws Exception {
        String passengerJson = """
                {
                  "name": "Joao do Teste",
                  "email": "dup@gmail.com",
                  "phone": "31999998888",
                  "password": "senhaSegura123"
                }
                """;

        // Registrar primeiro
        mockMvc.perform(post("/api/v1/auth/passenger/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passengerJson))
                .andExpect(status().isCreated());

        // Tentar registrar duplicado
        mockMvc.perform(post("/api/v1/auth/passenger/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passengerJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Email ja cadastrado")));
    }

    @Test
    public void shouldRegisterDriverSuccessfully() throws Exception {
        String driverJson = """
                {
                  "name": "Maria do Teste",
                  "vehiclePlate": "XYZ-9876",
                  "email": "maria.teste@gmail.com",
                  "password": "senhaSegura456"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/driver/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(driverJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Maria do Teste")))
                .andExpect(jsonPath("$.vehiclePlate", is("XYZ-9876")))
                .andExpect(jsonPath("$.email", is("maria.teste@gmail.com")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    public void shouldLoginPassengerSuccessfullyAndReceiveJWT() throws Exception {
        // Primeiro, cadastra
        String registerJson = """
                {
                  "name": "Ana Teste",
                  "email": "ana@gmail.com",
                  "phone": "31988887777",
                  "password": "senhaSecretaAna"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/passenger/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        // Tenta fazer login
        String loginJson = """
                {
                  "email": "ana@gmail.com",
                  "password": "senhaSecretaAna"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/passenger/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role", is("ROLE_PASSENGER")))
                .andExpect(jsonPath("$.name", is("Ana Teste")));
    }

    @Test
    public void shouldFailPassengerLoginWithInvalidPassword() throws Exception {
        String registerJson = """
                {
                  "name": "Ana Teste 2",
                  "email": "ana2@gmail.com",
                  "phone": "31988887777",
                  "password": "senhaSecretaAna"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/passenger/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated());

        String loginJson = """
                {
                  "email": "ana2@gmail.com",
                  "password": "senhaErrada"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/passenger/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Credenciais invalidas.")));
    }

    @Test
    public void shouldRequireAuthenticationForProtectedEndpoints() throws Exception {
        // Rota protegida sem token JWT deve ser bloqueada (SC_UNAUTHORIZED)
        mockMvc.perform(get("/api/v1/rides/some-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void shouldRejectCoreEndpointsWithoutApiKey() throws Exception {
        // Rota de delegação do Core sem chave X-API-Key deve ser barrada com 401
        String bidRequestJson = """
                {
                  "rideId": "550e8400-e29b-41d4-a716-446655440000",
                  "origin": { "latitude": -19.2012, "longitude": -46.2231 },
                  "destination": { "latitude": -19.2045, "longitude": -46.2290 },
                  "logicalTimestamp": 10
                }
                """;
        
        mockMvc.perform(post("/api/v1/delegation/bid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequestJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void shouldAllowCoreEndpointsWithValidApiKey() throws Exception {
        String bidRequestJson = """
                {
                  "rideId": "550e8400-e29b-41d4-a716-446655440000",
                  "origin": { "latitude": -19.2012, "longitude": -46.2231 },
                  "destination": { "latitude": -19.2045, "longitude": -46.2290 },
                  "logicalTimestamp": 10
                }
                """;

        // Envia com X-API-Key correto. Como não temos a controller de delegação implementada ainda na semana 1,
        // o endpoint deve retornar 404 Not Found em vez de 401 Unauthorized, mostrando que ele passou pelo filtro de segurança!
        mockMvc.perform(post("/api/v1/delegation/bid")
                        .header("X-API-Key", "core-shared-secret-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bidRequestJson))
                .andExpect(status().isNotFound()); // Indica que passou da autenticação, mas a rota em si ainda não existe!
    }
}
