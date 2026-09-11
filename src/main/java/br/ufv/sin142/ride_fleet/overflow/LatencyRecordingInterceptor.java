package br.ufv.sin142.ride_fleet.overflow;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;

/**
 * Alimenta a janela de latencia com a duracao de cada requisicao da API.
 *
 * Interceptor em vez de Filter por dois motivos concretos:
 *
 * 1. Exclusoes declarativas. O healthcheck do Docker Compose da Semana 2 vai
 *    bater em /health a cada poucos segundos com latencia submilissegundo. Essas
 *    medicoes arrastariam a media para perto de zero e deixariam o sinal de
 *    latencia praticamente morto. O interceptor e registrado por padrao de rota.
 *
 * 2. Mede a coisa certa. Um filtro antes da cadeia do Spring Security tambem
 *    mediria cada 401 rejeitado - uma rajada de requisicoes nao autenticadas nao e
 *    sinal de congestionamento, mas deslocaria a decisao de overflow. O interceptor
 *    roda depois do roteamento e da autenticacao, medindo trabalho de aplicacao.
 */
@Component
public class LatencyRecordingInterceptor implements HandlerInterceptor {

    static final String START_ATTRIBUTE = "ridefleet.request.startMillis";

    private final LatencyWindow latencyWindow;
    private final Clock clock;

    public LatencyRecordingInterceptor(LatencyWindow latencyWindow, Clock clock) {
        this.latencyWindow = latencyWindow;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_ATTRIBUTE, clock.millis());
        return true;
    }

    /**
     * afterCompletion, e nao postHandle, para que requisicoes encerradas em
     * excecao tambem entrem na medicao: elas sao justamente as que aparecem quando
     * o servico comeca a sofrer.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object start = request.getAttribute(START_ATTRIBUTE);
        if (start instanceof Long startMillis) {
            latencyWindow.record(clock.millis() - startMillis);
        }
    }
}
