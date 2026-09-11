package br.ufv.sin142.ride_fleet.overflow;

import br.ufv.sin142.ride_fleet.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do interceptor que alimenta a janela de latencia.
 */
class LatencyRecordingInterceptorTest {

    private MutableClock clock;
    private LatencyWindow window;
    private LatencyRecordingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        OverflowProperties props = new OverflowProperties();
        props.setLatencyWindow(Duration.ofSeconds(30));
        props.setLatencyWindowMinSamples(1);
        props.setLatencyWindowCapacity(16);

        clock = new MutableClock(Instant.parse("2026-05-10T12:00:00Z"));
        window = new LatencyWindow(props, clock);
        interceptor = new LatencyRecordingInterceptor(window, clock);
    }

    @Test
    @DisplayName("A duracao da requisicao e registrada na janela")
    void shouldRecordRequestDuration() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides/request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        clock.advance(Duration.ofMillis(250));
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(window.averageMillis()).hasValue(250.0);
    }

    @Test
    @DisplayName("Requisicao que terminou em excecao tambem e medida")
    void shouldRecordEvenWhenRequestFailed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rides/request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        clock.advance(Duration.ofMillis(80));
        // afterCompletion, e nao postHandle, justamente para cobrir este caso
        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("falhou"));

        assertThat(window.averageMillis()).hasValue(80.0);
    }

    @Test
    @DisplayName("Sem o marcador de inicio, nada e registrado")
    void shouldIgnoreRequestWithoutStartMarker() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rides/pending");

        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

        assertThat(window.sampleCount()).isZero();
    }

    @Test
    @DisplayName("preHandle nunca interrompe a cadeia")
    void preHandleShouldAlwaysContinue() throws Exception {
        boolean proceed = interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/v1/rides/pending"),
                new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
    }
}
