package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guardas de autorizacao sobre a corrida.
 *
 * A regra "so o dono ou o motorista atribuido" nao e expressavel por padrao de
 * rota, entao ela vive no RideService. Estes testes provam que ela vale de fato
 * sobre HTTP.
 */
class RideAuthorizationIntegrationTests extends IntegrationTestBase {

    private static final String SENHA = "senhaSegura123";
    private static final String DONO = "dono@exemplo.com";
    private static final String INTRUSO = "intruso@exemplo.com";
    private static final String MOTORISTA = "motorista.auth@exemplo.com";

    private String ownerToken;
    private String rideId;

    private void givenARideFromOwner() throws Exception {
        registerPassenger("Dono da Corrida", DONO, SENHA);
        registerPassenger("Passageiro Intruso", INTRUSO, SENHA);
        registerDriver("Motorista Auth", "AUT-1234", MOTORISTA, SENHA);

        ownerToken = loginPassenger(DONO, SENHA);

        String body = """
                {
                  "origin":      { "latitude": -19.2012, "longitude": -46.2231, "address": "A" },
                  "destination": { "latitude": -19.2045, "longitude": -46.2290, "address": "B" }
                }
                """;

        String json = mockMvc.perform(post("/api/v1/rides/request")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        rideId = objectMapper.readTree(json).get("rideId").asText();
    }

    @Test
    @DisplayName("O dono consulta a propria corrida")
    void ownerShouldReadOwnRide() throws Exception {
        givenARideFromOwner();

        mockMvc.perform(get("/api/v1/rides/{id}", rideId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rideId", is(rideId)));
    }

    @Test
    @DisplayName("Outro passageiro recebe 403 ao consultar corrida alheia")
    void thirdPartyShouldGetForbidden() throws Exception {
        givenARideFromOwner();
        String intrusoToken = loginPassenger(INTRUSO, SENHA);

        mockMvc.perform(get("/api/v1/rides/{id}", rideId)
                        .header("Authorization", bearer(intrusoToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Forbidden")));
    }

    @Test
    @DisplayName("passengerId de outra pessoa no corpo devolve 403")
    void shouldRejectRequestOnBehalfOfAnotherPassenger() throws Exception {
        registerPassenger("Dono da Corrida", DONO, SENHA);
        var outro = registerPassenger("Passageiro Intruso", INTRUSO, SENHA);
        String intrusoToken = loginPassenger(INTRUSO, SENHA);

        String donoId = passengerRepository.findByEmail(DONO).orElseThrow().getId().toString();

        String body = """
                {
                  "passengerId": "%s",
                  "origin":      { "latitude": -19.2012, "longitude": -46.2231, "address": "A" },
                  "destination": { "latitude": -19.2045, "longitude": -46.2290, "address": "B" }
                }
                """.formatted(donoId);

        // o intruso tenta pedir corrida em nome do dono trocando um campo do JSON
        mockMvc.perform(post("/api/v1/rides/request")
                        .header("Authorization", bearer(intrusoToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Motorista nao atribuido recebe 403 ao tentar aceitar")
    void unassignedDriverShouldNotAct() throws Exception {
        givenARideFromOwner();
        String driverToken = loginDriver(MOTORISTA, SENHA);

        // a corrida ficou em REQUEST, sem motorista atribuido
        mockMvc.perform(post("/api/v1/rides/{id}/accept", rideId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Passageiro nao acessa endpoints de motorista")
    void passengerShouldNotUseDriverEndpoints() throws Exception {
        givenARideFromOwner();

        mockMvc.perform(patch("/api/v1/drivers/me/status")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"AVAILABLE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Id malformado no caminho devolve 400, nao 500")
    void malformedUuidShouldReturnBadRequest() throws Exception {
        givenARideFromOwner();

        mockMvc.perform(get("/api/v1/rides/{id}", "nao-sou-um-uuid")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("Corrida inexistente devolve 404")
    void unknownRideShouldReturnNotFound() throws Exception {
        givenARideFromOwner();

        mockMvc.perform(get("/api/v1/rides/{id}", "550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("Coordenada invalida devolve 400 com erro por campo")
    void invalidCoordinateShouldReturnFieldError() throws Exception {
        registerPassenger("Dono da Corrida", DONO, SENHA);
        ownerToken = loginPassenger(DONO, SENHA);

        String body = """
                {
                  "origin":      { "latitude": 999.0, "longitude": -46.2231, "address": "A" },
                  "destination": { "latitude": -19.2045, "longitude": -46.2290, "address": "B" }
                }
                """;

        mockMvc.perform(post("/api/v1/rides/request")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }
}
