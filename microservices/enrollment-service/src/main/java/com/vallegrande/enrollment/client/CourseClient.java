package com.vallegrande.enrollment.client;

import com.vallegrande.enrollment.model.CourseDto;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class CourseClient {

    private static final Logger log = LoggerFactory.getLogger(CourseClient.class);

    private final WebClient webClient;
    private final long timeoutMs;

    public CourseClient(
            @Value("${services.course-service.url}") String courseServiceUrl,
            @Value("${services.course-service.timeout-ms:2500}") long timeoutMs) {
        this.timeoutMs = timeoutMs;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMs)
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));

        this.webClient = WebClient.builder()
                .baseUrl(courseServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        log.info("CourseClient configurado con baseUrl: {} y timeout: {} ms", courseServiceUrl, timeoutMs);
    }

    public CourseDto getCourse(String courseId) {
        log.info("Consultando información de curso [{}] en course-service...", courseId);
        try {
            return webClient.get()
                    .uri("/api/courses/{id}", courseId)
                    .retrieve()
                    .bodyToMono(CourseDto.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("El curso [{}] no fue encontrado en course-service (HTTP 404)", courseId);
                return null;
            }
            log.error("Error en respuesta de course-service: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Error en course-service: " + e.getMessage(), e);
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                log.error("TIMEOUT comunicando con course-service al obtener curso [{}] tras {} ms: {}", courseId, timeoutMs, e.getMessage());
                throw new CourseServiceTimeoutException("Timeout superado (" + timeoutMs + " ms) esperando respuesta de course-service", e);
            }
            log.error("Fallo de comunicación con course-service: {}", e.getMessage());
            throw new RuntimeException("Error conectando con course-service: " + e.getMessage(), e);
        }
    }

    public CourseDto reserveSeat(String courseId) {
        log.info("Solicitando reserva de vacante para curso [{}] en course-service...", courseId);
        try {
            return webClient.put()
                    .uri("/api/courses/{id}/enroll", courseId)
                    .retrieve()
                    .bodyToMono(CourseDto.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.warn("No hay vacantes disponibles para el curso [{}] (HTTP 400)", courseId);
                throw new IllegalStateException("El curso no dispone de vacantes para confirmar la matrícula");
            }
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Curso [{}] no encontrado al intentar reservar vacante", courseId);
                throw new IllegalArgumentException("El curso especificado no existe o no está activo");
            }
            log.error("Error devuelto por course-service al reservar vacante: status={}", e.getStatusCode());
            throw new RuntimeException("Error al procesar reserva en course-service: " + e.getMessage(), e);
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                log.error("TIMEOUT al reservar vacante en course-service para curso [{}] tras {} ms: {}", courseId, timeoutMs, e.getMessage());
                throw new CourseServiceTimeoutException("Timeout superado (" + timeoutMs + " ms) esperando respuesta de course-service", e);
            }
            log.error("Error de comunicación al reservar vacante: {}", e.getMessage());
            throw new RuntimeException("Error conectando con course-service: " + e.getMessage(), e);
        }
    }

    public void releaseSeat(String courseId) {
        try {
            webClient.put()
                    .uri("/api/courses/{id}/release", courseId)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            log.info("Vacante liberada para el curso [{}]", courseId);
        } catch (Exception e) {
            log.warn("No se pudo liberar la vacante del curso [{}] tras cancelación: {}", courseId, e.getMessage());
        }
    }

    private boolean isTimeoutException(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof TimeoutException ||
                curr instanceof io.netty.handler.timeout.ReadTimeoutException ||
                curr instanceof java.util.concurrent.TimeoutException ||
                (curr.getMessage() != null && curr.getMessage().toLowerCase().contains("timeout"))) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public static class CourseServiceTimeoutException extends RuntimeException {
        public CourseServiceTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
