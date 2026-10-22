package com.vallegrande.course.service;

import com.vallegrande.course.model.Course;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final Map<String, Course> courseRepository = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    @Value("${simulation.artificial-delay-ms:0}")
    private long artificialDelayMs;

    @Value("${simulation.crash:false}")
    private boolean simulateCrash;

    // Micrometer Metrics (Counter, Gauge, Timer)
    private Counter requestsCounter;
    private Counter creationsCounter;
    private Counter seatsDiscountedCounter;
    private Timer operationTimer;
    private final AtomicLong delayGaugeValue = new AtomicLong(0);

    public CourseService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        if (simulateCrash) {
            log.error("CRITICAL: SIMULATE_CRASH is set to true! Forcing application startup failure for CrashLoopBackOff demonstration.");
            throw new IllegalStateException("CRITICAL ERROR: Simulated fatal configuration failure on startup.");
        }

        delayGaugeValue.set(artificialDelayMs);

        // 1. Counter metrics
        this.requestsCounter = Counter.builder("course_requests_total")
                .description("Total de solicitudes procesadas por course-service")
                .tag("service", "course-service")
                .register(meterRegistry);

        this.creationsCounter = Counter.builder("course_creations_total")
                .description("Total de cursos creados")
                .tag("service", "course-service")
                .register(meterRegistry);

        this.seatsDiscountedCounter = Counter.builder("course_seats_discounted_total")
                .description("Total de vacantes descontadas por matrículas")
                .tag("service", "course-service")
                .register(meterRegistry);

        // 2. Timer metric
        this.operationTimer = Timer.builder("course_operation_duration_seconds")
                .description("Duración de operaciones en course-service")
                .tag("service", "course-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 3. Gauge metrics
        meterRegistry.gauge("course_available_seats_gauge", this, CourseService::getTotalAvailableSeats);
        meterRegistry.gauge("course_artificial_delay_ms", delayGaugeValue, AtomicLong::get);

        // Datos iniciales en memoria
        saveCourse(new Course("CRS-001", "Kubernetes & Observabilidad Cloud Native", "Prof. Carlos Mendoza", "DevOps", 25, 25, 120.0, true));
        saveCourse(new Course("CRS-002", "Microservicios Resilientes con Spring Boot 3", "Ing. Diego Alva", "Backend", 20, 20, 150.0, true));
        saveCourse(new Course("CRS-003", "Monitoreo con Prometheus & Grafana", "Dra. Elena Ramos", "DevOps", 15, 15, 180.0, true));
        saveCourse(new Course("CRS-004", "Seguridad y Redes en Kubernetes", "Lic. Fernando Ruiz", "Cloud", 10, 10, 140.0, true));

        log.info("CourseService inicializado con {} cursos en memoria. Delay artificial configurado: {} ms", courseRepository.size(), artificialDelayMs);
    }

    private void applyArtificialDelay() {
        long delay = delayGaugeValue.get();
        if (delay > 0) {
            try {
                log.warn("Aplicando delay artificial de {} ms en respuesta de course-service...", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Delay artificial interrumpido", e);
            }
        }
    }

    public List<Course> getAllCourses() {
        requestsCounter.increment();
        return operationTimer.record(() -> {
            applyArtificialDelay();
            return new ArrayList<>(courseRepository.values());
        });
    }

    public Optional<Course> getCourseById(String id) {
        requestsCounter.increment();
        return operationTimer.record(() -> {
            applyArtificialDelay();
            return Optional.ofNullable(courseRepository.get(id));
        });
    }

    public Course saveCourse(Course course) {
        requestsCounter.increment();
        return operationTimer.record(() -> {
            applyArtificialDelay();
            courseRepository.put(course.getId(), course);
            creationsCounter.increment();
            return course;
        });
    }

    public synchronized Optional<Course> reserveSeat(String courseId) {
        requestsCounter.increment();
        return operationTimer.record(() -> {
            applyArtificialDelay();
            Course course = courseRepository.get(courseId);
            if (course == null || !course.isActive()) {
                return Optional.empty();
            }
            if (course.getAvailableSeats() <= 0) {
                throw new IllegalStateException("El curso " + courseId + " no tiene vacantes disponibles");
            }
            course.setAvailableSeats(course.getAvailableSeats() - 1);
            seatsDiscountedCounter.increment();
            return Optional.of(course);
        });
    }

    public synchronized Optional<Course> releaseSeat(String courseId) {
        requestsCounter.increment();
        return operationTimer.record(() -> {
            applyArtificialDelay();
            Course course = courseRepository.get(courseId);
            if (course == null) {
                return Optional.empty();
            }
            if (course.getAvailableSeats() < course.getTotalSeats()) {
                course.setAvailableSeats(course.getAvailableSeats() + 1);
            }
            return Optional.of(course);
        });
    }

    public double getTotalAvailableSeats() {
        return courseRepository.values().stream()
                .filter(Course::isActive)
                .mapToInt(Course::getAvailableSeats)
                .sum();
    }

    public void setArtificialDelayMs(long delayMs) {
        this.delayGaugeValue.set(delayMs);
        log.info("Delay artificial actualizado dinámicamente a {} ms", delayMs);
    }

    public long getArtificialDelayMs() {
        return this.delayGaugeValue.get();
    }

    public int getCoursesCount() {
        return courseRepository.size();
    }
}
