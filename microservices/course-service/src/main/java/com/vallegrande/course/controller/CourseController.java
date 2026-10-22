package com.vallegrande.course.controller;

import com.vallegrande.course.model.Course;
import com.vallegrande.course.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Endpoint de health check propio:
     * Responde de inmediato sin depender de otros servicios ni componentes externos.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "course-service",
                "timestamp", Instant.now().toString(),
                "coursesCount", courseService.getCoursesCount(),
                "availableSeats", courseService.getTotalAvailableSeats(),
                "artificialDelayMs", courseService.getArtificialDelayMs()
        ));
    }

    /**
     * Endpoint 1 de negocio: Listar todos los cursos
     */
    @GetMapping("/api/courses")
    public ResponseEntity<List<Course>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    /**
     * Endpoint 2 de negocio: Obtener detalle de un curso por ID
     */
    @GetMapping("/api/courses/{id}")
    public ResponseEntity<?> getCourseById(@PathVariable String id) {
        return courseService.getCourseById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Curso no encontrado", "courseId", id)));
    }

    /**
     * Endpoint 3 de negocio: Registrar un nuevo curso
     */
    @PostMapping("/api/courses")
    public ResponseEntity<Course> createCourse(@RequestBody Course course) {
        if (course.getId() == null || course.getId().isBlank()) {
            course.setId("CRS-" + (courseService.getCoursesCount() + 1));
        }
        if (course.getAvailableSeats() <= 0 && course.getTotalSeats() > 0) {
            course.setAvailableSeats(course.getTotalSeats());
        }
        course.setActive(true);
        Course saved = courseService.saveCourse(course);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Endpoint 4 de negocio: Descontar vacante para matrícula
     * Utilizado por el Servicio B (enrollment-service) para validar y reservar cupo
     */
    @PutMapping("/api/courses/{id}/enroll")
    public ResponseEntity<?> reserveSeat(@PathVariable String id) {
        try {
            return courseService.reserveSeat(id)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Curso inactivo o inexistente", "courseId", id)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "courseId", id));
        }
    }

    /**
     * Endpoint auxiliar: Liberar vacante por cancelación de matrícula
     */
    @PutMapping("/api/courses/{id}/release")
    public ResponseEntity<?> releaseSeat(@PathVariable String id) {
        return courseService.releaseSeat(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Curso no encontrado", "courseId", id)));
    }

    /**
     * Endpoint de gestión para Casuística 2 (Simulación de Servicio Lento)
     */
    @PostMapping("/api/courses/admin/delay")
    public ResponseEntity<Map<String, Object>> updateDelay(@RequestBody Map<String, Long> payload) {
        Long delay = payload.getOrDefault("delayMs", 0L);
        courseService.setArtificialDelayMs(delay);
        return ResponseEntity.ok(Map.of(
                "message", "Delay artificial actualizado correctamente",
                "newDelayMs", delay
        ));
    }
}
