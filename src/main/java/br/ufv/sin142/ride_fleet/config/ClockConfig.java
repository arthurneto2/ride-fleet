package br.ufv.sin142.ride_fleet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Expoe o relogio do sistema como bean.
 *
 * Injetar Clock em vez de chamar System.currentTimeMillis direto e o que
 * permite testar janelas temporais de forma deterministica, sem Thread.sleep.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
