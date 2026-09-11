package br.ufv.sin142.ride_fleet.ride;

import br.ufv.sin142.ride_fleet.driver.DriverStatus;
import br.ufv.sin142.ride_fleet.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo ponta a ponta da corrida sobre HTTP real.
 *
 * Cobre o caminho principal exigido pela Semana 1:
 * REQUEST -> MATCH -> CONFIRM -> IN_TRANSIT -> COMPLETE
 */
class RideFlowIntegrationTests extends IntegrationTestBase {

    private static final String PASSENGER_EMAIL = "joao.fluxo@exemplo.com";
    private static final String DRIVER_EMAIL = "maria.fluxo@exemplo.com";
    private static final String SENHA = "senhaSegura123";

    private String passengerToken;
    private String driverToken;

    private void givenPassengerAndAvailableDriver() throws Exception {
        registerPassenger("Joao do Fluxo", PASSENGER_EMAIL, SENHA);
        registerDriver("Maria do Fluxo", "FLU-1234", DRIVER_EMAIL, SENHA);

        passengerToken = loginPassenger(PASSENGER_EMAIL, SENHA);
        driverToken = loginDriver(DRIVER_EMAIL, SENHA);

        // sem corrida pendente no pool, entrar em servico deixa o motorista livre
        goOnline().andExpect(jsonPath("$.status", is("AVAILABLE")));
    }

    /**
     * Entrar em servico. O status resultante NAO e necessariamente AVAILABLE: se
     * houver corrida pendente no pool, o servico a atribui na hora e o motorista
     * ja sai como IN_RIDE. Por isso a assercao de status fica com cada teste.
     */
    private ResultActions goOnline() throws Exception {
        return mockMvc.perform(patch("/api/v1/drivers/me/status")
                        .header("Authorization", bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"AVAILABLE\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode requestRide() throws Exception {
        String body = """
                {
                  "origin":      { "latitude": -19.2012, "longitude": -46.2231, "address": "Predio de Aulas - UFV" },
                  "destination": { "latitude": -19.2045, "longitude": -46.2290, "address": "RU - UFV" }
                }
                """;

        String json = mockMvc.perform(post("/api/v1/rides/request")
                        .header("Authorization", bearer(passengerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("Fluxo completo: solicitar, aceitar, iniciar e concluir")
    void shouldWalkTheCompleteHappyPath() throws Exception {
        givenPassengerAndAvailableDriver();

        JsonNode ride = requestRide();
        String rideId = ride.get("rideId").asText();

        // 202 Accepted com motorista ja atribuido e preco estimado
        assertThat(ride.get("status").asText()).isEqualTo("MATCH");
        assertThat(ride.get("driverId").isNull()).isFalse();
        assertThat(ride.get("price").isNull()).isFalse();
        assertThat(ride.get("etaSeconds").asInt()).isPositive();

        mockMvc.perform(post("/api/v1/rides/{id}/accept", rideId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRM")));

        mockMvc.perform(post("/api/v1/rides/{id}/start", rideId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_TRANSIT")));

        mockMvc.perform(post("/api/v1/rides/{id}/complete", rideId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETE")));

        // motorista volta a ficar disponivel
        mockMvc.perform(get("/api/v1/drivers/me")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("AVAILABLE")));
    }

    @Test
    @DisplayName("O relogio logico cresce a cada transicao")
    void logicalTimestampShouldGrowAlongTheFlow() throws Exception {
        givenPassengerAndAvailableDriver();

        JsonNode ride = requestRide();
        String rideId = ride.get("rideId").asText();
        long afterMatch = ride.get("logicalTimestamp").asLong();

        String confirmJson = mockMvc.perform(post("/api/v1/rides/{id}/accept", rideId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long afterConfirm = objectMapper.readTree(confirmJson).get("logicalTimestamp").asLong();
        assertThat(afterConfirm).isGreaterThan(afterMatch);
    }

    @Test
    @DisplayName("Pular etapa do fluxo devolve 409")
    void shouldRejectSkippingAStep() throws Exception {
        givenPassengerAndAvailableDriver();
        String rideId = requestRide().get("rideId").asText();

        // a corrida esta em MATCH; concluir sem aceitar e iniciar e invalido
        mockMvc.perform(post("/api/v1/rides/{id}/complete", rideId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")));
    }

    @Test
    @DisplayName("Recusar devolve a corrida ao pool e libera o motorista")
    void declineShouldReturnRideToPool() throws Exception {
        givenPassengerAndAvailableDriver();
        String rideId = requestRide().get("rideId").asText();

        mockMvc.perform(post("/api/v1/rides/{id}/decline", rideId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REQUEST")))
                .andExpect(jsonPath("$.driverId", is(nullValue())));

        mockMvc.perform(get("/api/v1/drivers/me")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(jsonPath("$.status", is("AVAILABLE")));
    }

    @Test
    @DisplayName("Sem motorista disponivel, a corrida fica no pool marcada para delegacao")
    void shouldKeepRideInPoolWhenNoDriverIsAvailable() throws Exception {
        registerPassenger("Joao Sozinho", PASSENGER_EMAIL, SENHA);
        registerDriver("Maria Offline", "OFF-0001", DRIVER_EMAIL, SENHA);
        passengerToken = loginPassenger(PASSENGER_EMAIL, SENHA);
        driverToken = loginDriver(DRIVER_EMAIL, SENHA);
        // motorista permanece OFFLINE: nenhum motorista em AVAILABLE

        JsonNode ride = requestRide();

        assertThat(ride.get("status").asText()).isEqualTo("REQUEST");
        assertThat(ride.get("awaitingDelegation").asBoolean()).isTrue();
        assertThat(ride.get("driverId").isNull()).isTrue();
        assertThat(ride.get("overflowReason").asText()).isEqualTo("NO_AVAILABLE_DRIVERS");
    }

    @Test
    @DisplayName("Corrida marcada para delegacao ainda e atendida localmente se um motorista aparecer")
    void overflowedRideShouldStillBeServedLocally() throws Exception {
        registerPassenger("Joao Paciente", PASSENGER_EMAIL, SENHA);
        registerDriver("Maria Atrasada", "LAT-0002", DRIVER_EMAIL, SENHA);
        passengerToken = loginPassenger(PASSENGER_EMAIL, SENHA);
        driverToken = loginDriver(DRIVER_EMAIL, SENHA);

        String rideId = requestRide().get("rideId").asText();

        // o motorista entra em servico depois da solicitacao; ao ficar disponivel,
        // o servico drena o pool de pendentes e ja assume a corrida que esperava
        goOnline().andExpect(jsonPath("$.status", is("IN_RIDE")));

        mockMvc.perform(get("/api/v1/rides/{id}", rideId)
                        .header("Authorization", bearer(passengerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("MATCH")))
                .andExpect(jsonPath("$.driverId", is(notNullValue())))
                .andExpect(jsonPath("$.awaitingDelegation", is(false)));
    }

    @Test
    @DisplayName("O pool de pendentes lista as corridas em ordem de chegada")
    void pendingPoolShouldListRidesInArrivalOrder() throws Exception {
        registerPassenger("Joao Fila", PASSENGER_EMAIL, SENHA);
        registerDriver("Maria Fila", "FIL-0003", DRIVER_EMAIL, SENHA);
        passengerToken = loginPassenger(PASSENGER_EMAIL, SENHA);
        driverToken = loginDriver(DRIVER_EMAIL, SENHA);

        requestRide();
        requestRide();

        mockMvc.perform(get("/api/v1/rides/pending")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    @DisplayName("Passageiro cancela a corrida e o motorista e liberado")
    void passengerShouldCancelRide() throws Exception {
        givenPassengerAndAvailableDriver();
        String rideId = requestRide().get("rideId").asText();

        mockMvc.perform(post("/api/v1/rides/{id}/cancel", rideId)
                        .header("Authorization", bearer(passengerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Mudei de ideia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(driverRepository.findByEmail(DRIVER_EMAIL).orElseThrow().getStatus())
                .isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Historico proprio do passageiro lista suas corridas")
    void passengerShouldSeeOwnHistory() throws Exception {
        givenPassengerAndAvailableDriver();
        requestRide();

        mockMvc.perform(get("/api/v1/rides/me")
                        .header("Authorization", bearer(passengerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));
    }
}
