package com.vallegrande.enrollment.service;

import com.vallegrande.enrollment.client.CourseClient;
import com.vallegrande.enrollment.model.CourseDto;
import com.vallegrande.enrollment.model.Enrollment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final Map<String, Enrollment> enrollmentRepository = new ConcurrentHashMap<>();
    private final CourseClient courseClient;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger enrollmentIdSequence = new AtomicInteger(1000);

    @Value("${simulation.crash:false}")
    private boolean simulateCrash;

    // Micrometer Metrics (Counter, Gauge, Timer)
    private Counter requestsCounter;
    private Counter successCounter;
    private Counter failuresCounter;
    private Counter timeoutsCounter;
    private Timer processingTimer;

    public EnrollmentService(CourseClient courseClient, MeterRegistry meterRegistry) {
        this.courseClient = courseClient;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        if (simulateCrash) {
            log.error("CRITICAL: SIMULATE_CRASH is set to true! Forcing application startup failure for CrashLoopBackOff demonstration.");
            throw new IllegalStateException("CRITICAL ERROR: Simulated fatal configuration failure on startup in enrollment-service.");
        }

        // 1. Counters
        this.requestsCounter = Counter.builder("enrollment_requests_total")
                .description("Total de solicitudes de matrícula recibidas")
                .tag("service", "enrollment-service")
                .register(meterRegistry);

        this.successCounter = Counter.builder("enrollment_success_total")
                .description("Total de matrículas confirmadas exitosamente")
                .tag("service", "enrollment-service")
                .register(meterRegistry);

        this.failuresCounter = Counter.builder("enrollment_failures_total")
                .description("Total de matrículas fallidas por cualquier causa")
                .tag("service", "enrollment-service")
                .register(meterRegistry);

        this.timeoutsCounter = Counter.builder("enrollment_timeouts_total")
                .description("Total de timeouts experimentados consumiendo course-service")
                .tag("service", "enrollment-service")
                .register(meterRegistry);

        // 2. Timer
        this.processingTimer = Timer.builder("enrollment_processing_duration_seconds")
                .description("Tiempo total de procesamiento de matrículas incluyendo llamada HTTP")
                .tag("service", "enrollment-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 3. Gauge
        meterRegistry.gauge("enrollment_active_count_gauge", this, EnrollmentService::getActiveEnrollmentsCount);

        // Semilla inicial de matrículas
        Enrollment e1 = new Enrollment("ENR-1001", "Lucía Navarro", "lucia.navarro@vallegrande.edu.pe", "CRS-001", "Kubernetes & Observabilidad Cloud Native", "CONFIRMED", Instant.now().toString(), 120.0);
        Enrollment e2 = new Enrollment("ENR-1002", "Renato Díaz", "renato.diaz@vallegrande.edu.pe", "CRS-002", "Microservicios Resilientes con Spring Boot 3", "CONFIRMED", Instant.now().toString(), 150.0);
        enrollmentRepository.put(e1.getId(), e1);
        enrollmentRepository.put(e2.getId(), e2);

        log.info("EnrollmentService inicializado con {} matrículas en memoria", enrollmentRepository.size());
    }

    public List<Enrollment> getAllEnrollments() {
        requestsCounter.increment();
        return new ArrayList<>(enrollmentRepository.values());
    }

    public Optional<Enrollment> getEnrollmentById(String id) {
        requestsCounter.increment();
        return Optional.ofNullable(enrollmentRepository.get(id));
    }

    public Enrollment processEnrollment(String studentName, String studentEmail, String courseId) {
        requestsCounter.increment();

        return processingTimer.record(() -> {
            log.info("Iniciando flujo de matrícula para alumno: [{}] en curso: [{}]", studentName, courseId);
            try {
                // Paso 1: Validar existencia del curso en course-service
                CourseDto course = courseClient.getCourse(courseId);
                if (course == null) {
                    failuresCounter.increment();
                    throw new IllegalArgumentException("El curso solicitado [" + courseId + "] no existe en el catálogo");
                }

                if (!course.isActive()) {
                    failuresCounter.increment();
                    throw new IllegalStateException("El curso [" + courseId + "] se encuentra cerrado para inscripciones");
                }

                // Paso 2: Descontar vacante en course-service vía llamada HTTP
                CourseDto updatedCourse = courseClient.reserveSeat(courseId);

                // Paso 3: Confirmar y persistir matrícula en memoria
                String enrollmentId = "ENR-" + enrollmentIdSequence.incrementAndGet();
                Enrollment enrollment = new Enrollment(
                        enrollmentId,
                        studentName,
                        studentEmail,
                        courseId,
                        updatedCourse.getTitle(),
                        "CONFIRMED",
                        Instant.now().toString(),
                        updatedCourse.getPrice()
                );

                enrollmentRepository.put(enrollmentId, enrollment);
                successCounter.increment();
                log.info("Matrícula [{}] confirmada exitosamente para alumno [{}]", enrollmentId, studentName);
                return enrollment;

            } catch (CourseClient.CourseServiceTimeoutException e) {
                timeoutsCounter.increment();
                failuresCounter.increment();
                log.error("Fallo por timeout al procesar matrícula para curso [{}]: {}", courseId, e.getMessage());
                throw e;
            } catch (Exception e) {
                failuresCounter.increment();
                log.error("Fallo general al procesar matrícula para curso [{}]: {}", courseId, e.getMessage());
                throw e;
            }
        });
    }

    public Optional<Enrollment> cancelEnrollment(String enrollmentId) {
        requestsCounter.increment();
        Enrollment enrollment = enrollmentRepository.get(enrollmentId);
        if (enrollment == null) {
            return Optional.empty();
        }

        enrollment.setStatus("CANCELLED");
        // Notificar a course-service para liberar cupo
        courseClient.releaseSeat(enrollment.getCourseId());
        log.info("Matrícula [{}] cancelada y vacante liberada", enrollmentId);
        return Optional.of(enrollment);
    }

    public double getActiveEnrollmentsCount() {
        return enrollmentRepository.values().stream()
                .filter(e -> "CONFIRMED".equalsIgnoreCase(e.getStatus()))
                .count();
    }

    public int getTotalEnrollmentsCount() {
        return enrollmentRepository.size();
    }
}
