package com.vallegrande.enrollment.controller;

import com.vallegrande.enrollment.client.CourseClient;
import com.vallegrande.enrollment.model.Enrollment;
import com.vallegrande.enrollment.service.EnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * Endpoint de health check propio:
     * Responde rápido e independiente sin depender de otros servicios ni componentes externos.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "enrollment-service",
                "timestamp", Instant.now().toString(),
                "totalEnrollments", enrollmentService.getTotalEnrollmentsCount(),
                "activeEnrollments", enrollmentService.getActiveEnrollmentsCount()
        ));
    }

    /**
     * Endpoint 1 de negocio: Listar todas las matrículas
     */
    @GetMapping("/api/enrollments")
    public ResponseEntity<List<Enrollment>> getAllEnrollments() {
        return ResponseEntity.ok(enrollmentService.getAllEnrollments());
    }

    /**
     * Endpoint 2 de negocio: Consultar matrícula por ID
     */
    @GetMapping("/api/enrollments/{id}")
    public ResponseEntity<?> getEnrollmentById(@PathVariable String id) {
        return enrollmentService.getEnrollmentById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Matrícula no encontrada", "enrollmentId", id)));
    }

    /**
     * Endpoint 3 de negocio: Registrar matrícula (comunica con course-service)
     * Si course-service no responde en tiempo, responde HTTP 504 de manera controlada
     */
    @PostMapping("/api/enrollments")
    public ResponseEntity<?> createEnrollment(@RequestBody Map<String, String> request) {
        String studentName = request.get("studentName");
        String studentEmail = request.get("studentEmail");
        String courseId = request.get("courseId");

        if (studentName == null || studentEmail == null || courseId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Parámetros incompletos. Se requiere studentName, studentEmail y courseId."
            ));
        }

        try {
            Enrollment created = enrollmentService.processEnrollment(studentName, studentEmail, courseId);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (CourseClient.CourseServiceTimeoutException e) {
            // Manejo de error controlado por Timeout al consultar Servicio A (Casuística 2)
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                    "error", "Gateway Timeout",
                    "message", "El servicio de cursos no respondió en el tiempo límite configurado.",
                    "details", e.getMessage(),
                    "timestamp", Instant.now().toString()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Recurso no encontrado",
                    "message", e.getMessage()
            ));

        } catch (IllegalStateException e) {
            // Error de negocio: curso sin vacantes
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Regla de Negocio",
                    "message", e.getMessage()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Error interno",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Endpoint 4 de negocio: Cancelar matrícula
     */
    @DeleteMapping("/api/enrollments/{id}")
    public ResponseEntity<?> cancelEnrollment(@PathVariable String id) {
        return enrollmentService.cancelEnrollment(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Matrícula no encontrada", "enrollmentId", id)));
    }
}
