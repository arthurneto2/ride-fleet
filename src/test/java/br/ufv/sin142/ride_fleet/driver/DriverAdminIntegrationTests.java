package br.ufv.sin142.ride_fleet.driver;

import br.ufv.sin142.ride_fleet.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD de motorista pelo administrador, mais as guardas de autorizacao.
 */
class DriverAdminIntegrationTests extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@ridefleet.local";
    private static final String DRIVER_EMAIL = "motorista.crud@exemplo.com";
    private static final String SENHA = "senhaSegura123";

    private String adminToken;
    private String driverId;

    private void givenAdminAndDriver() throws Exception {
        createAdmin(ADMIN_EMAIL, SENHA);
        adminToken = loginAdmin(ADMIN_EMAIL, SENHA);

        JsonNode driver = registerDriver("Motorista CRUD", "CRU-1234", DRIVER_EMAIL, SENHA);
        driverId = driver.get("id").asText();
    }

    @Test
    @DisplayName("Administrador lista os motoristas")
    void adminShouldListDrivers() throws Exception {
        givenAdminAndDriver();

        mockMvc.perform(get("/api/v1/drivers")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].email", is(DRIVER_EMAIL)));
    }

    @Test
    @DisplayName("Administrador atualiza um motorista")
    void adminShouldUpdateDriver() throws Exception {
        givenAdminAndDriver();

        String body = """
                { "name": "Nome Atualizado", "vehiclePlate": "NOV-9999",
                  "email": "%s", "active": true }
                """.formatted(DRIVER_EMAIL);

        mockMvc.perform(put("/api/v1/drivers/{id}", driverId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Nome Atualizado")))
                .andExpect(jsonPath("$.vehiclePlate", is("NOV-9999")));
    }

    @Test
    @DisplayName("Exclusao e logica: a linha permanece, marcada como inativa")
    void deleteShouldBeLogical() throws Exception {
        givenAdminAndDriver();

        mockMvc.perform(delete("/api/v1/drivers/{id}", driverId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        // a linha continua no banco, preservando o historico de corridas
        assertThat(driverRepository.findByEmail(DRIVER_EMAIL)).isPresent();
        assertThat(driverRepository.findByEmail(DRIVER_EMAIL).orElseThrow().getActive()).isFalse();
    }

    @Test
    @DisplayName("Excluir duas vezes e idempotente")
    void deleteShouldBeIdempotent() throws Exception {
        givenAdminAndDriver();

        mockMvc.perform(delete("/api/v1/drivers/{id}", driverId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/drivers/{id}", driverId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Motorista excluido logicamente nao consegue mais entrar")
    void softDeletedDriverShouldNotLogin() throws Exception {
        givenAdminAndDriver();

        mockMvc.perform(delete("/api/v1/drivers/{id}", driverId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        String body = """
                { "email": "%s", "password": "%s" }
                """.formatted(DRIVER_EMAIL, SENHA);

        // mesma mensagem de senha errada: nao revela que a conta foi desativada
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/driver/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Credenciais invalidas.")));
    }

    @Test
    @DisplayName("Administrador nao pode definir IN_RIDE manualmente")
    void adminShouldNotForceInRide() throws Exception {
        givenAdminAndDriver();

        mockMvc.perform(put("/api/v1/drivers/{id}/status", driverId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_RIDE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Motorista comum nao acessa a listagem de administrador")
    void driverShouldNotListAllDrivers() throws Exception {
        givenAdminAndDriver();
        String driverToken = loginDriver(DRIVER_EMAIL, SENHA);

        mockMvc.perform(get("/api/v1/drivers")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Forbidden")));
    }

    @Test
    @DisplayName("Motorista gerencia o proprio perfil sem ser administrador")
    void driverShouldManageOwnProfile() throws Exception {
        givenAdminAndDriver();
        String driverToken = loginDriver(DRIVER_EMAIL, SENHA);

        mockMvc.perform(get("/api/v1/drivers/me")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(DRIVER_EMAIL)));

        mockMvc.perform(patch("/api/v1/drivers/me/location")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": -19.2012, \"longitude\": -46.2231}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentLatitude", is(-19.2012)));
    }

    @Test
    @DisplayName("O Core consulta a listagem de motoristas com X-API-Key")
    void coreShouldListDriversWithApiKey() throws Exception {
        givenAdminAndDriver();

        // a Semana 3 precisa avaliar disponibilidade antes de responder a um leilao
        mockMvc.perform(get("/api/v1/drivers")
                        .header("X-API-Key", "core-shared-secret-key-123"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cadastro com placa duplicada devolve 400, nao erro de banco")
    void duplicatePlateShouldReturnBadRequest() throws Exception {
        givenAdminAndDriver();

        String body = """
                { "name": "Outro", "vehiclePlate": "cru-1234",
                  "email": "outro@exemplo.com", "password": "senhaSegura123" }
                """;

        // placa em minusculas e a mesma placa: a normalizacao detecta
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/driver/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Placa ja cadastrada para outro motorista.")));
    }
}
