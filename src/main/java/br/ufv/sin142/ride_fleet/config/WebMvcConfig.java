package br.ufv.sin142.ride_fleet.config;

import br.ufv.sin142.ride_fleet.overflow.LatencyRecordingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final LatencyRecordingInterceptor latencyRecordingInterceptor;

    public WebMvcConfig(LatencyRecordingInterceptor latencyRecordingInterceptor) {
        this.latencyRecordingInterceptor = latencyRecordingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(latencyRecordingInterceptor)
                .addPathPatterns("/api/**")
                // o healthcheck responde em submilissegundos e arrastaria a media
                // para perto de zero, matando o sinal de latencia do overflow
                .excludePathPatterns("/actuator/**", "/health");
    }
}
