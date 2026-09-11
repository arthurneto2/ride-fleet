package br.ufv.sin142.ride_fleet.overflow;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limites da politica de overflow.
 *
 * Todos os campos tem valor default aqui no Java, e nao apenas no
 * application.yml, porque src/test/resources/application.yml substitui
 * integralmente o arquivo principal: chave declarada so no yml de producao
 * nao existe nos testes.
 */
@Component
@ConfigurationProperties(prefix = "ridefleet.overflow")
@Getter
@Setter
public class OverflowProperties {

    /**
     * Interruptor geral. Desligado, o servico nunca se considera congestionado.
     * Existe para controlar a demo: primeiro o cenario local, depois a delegacao.
     */
    private boolean enabled = true;

    /** Congestionado quando as corridas pendentes passam estritamente deste numero. */
    private int pendingRidesThreshold = 5;

    /** Congestionado quando a latencia media recente passa deste valor. */
    private long averageLatencyThresholdMs = 800L;

    /** Span da janela deslizante de latencia. */
    private Duration latencyWindow = Duration.ofSeconds(30);

    /**
     * Minimo de amostras na janela para que a latencia seja considerada.
     * Evita delegar por causa de uma unica requisicao de cold start.
     */
    private int latencyWindowMinSamples = 10;

    /** Tamanho do buffer circular de amostras. */
    private int latencyWindowCapacity = 512;
}
