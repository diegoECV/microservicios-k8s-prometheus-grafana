#!/usr/bin/env bash
# ==============================================================================
# Script de Pruebas de Carga y Simulación de Tráfico
# Valle Grande - Observabilidad de Microservicios
# ==============================================================================


# Configuración por defecto (localhost via port-forward o IP de Minikube)
COURSE_URL="http://localhost:8081"
ENROLLMENT_URL="http://localhost:8082"
TOTAL_REQUESTS=50
SLEEP_INTERVAL=0.2
MODE="normal" # normal | timeout | exhaust | health

print_help() {
    echo "Uso: $0 [opciones]"
    echo ""
    echo "Opciones:"
    echo "  -n <numero>      Número total de peticiones a enviar (defecto: 50)"
    echo "  -i <segundos>    Intervalo de espera entre peticiones (defecto: 0.2)"
    echo "  -m <modo>        Modo de tráfico: 'normal', 'timeout', 'exhaust', 'health' (defecto: normal)"
    echo "  -c <url>         URL de course-service (defecto: $COURSE_URL)"
    echo "  -e <url>         URL de enrollment-service (defecto: $ENROLLMENT_URL)"
    echo "  -h               Mostrar esta ayuda"
    echo ""
    echo "Ejemplos:"
    echo "  $0 -n 100 -i 0.1 -m normal"
    echo "  $0 -n 30 -m timeout"
    echo "  $0 -n 25 -m exhaust"
}

while getopts "n:i:m:c:e:h" opt; do
    case ${opt} in
        n ) TOTAL_REQUESTS=$OPTARG ;;
        i ) SLEEP_INTERVAL=$OPTARG ;;
        m ) MODE=$OPTARG ;;
        c ) COURSE_URL=$OPTARG ;;
        e ) ENROLLMENT_URL=$OPTARG ;;
        h ) print_help; exit 0 ;;
        \? ) print_help; exit 1 ;;
    esac
done

echo "=========================================================="
echo "Iniciando generador de tráfico para microservicios"
echo "  - Modo:                $MODE"
echo "  - Peticiones totales:  $TOTAL_REQUESTS"
echo "  - Intervalo:           ${SLEEP_INTERVAL}s"
echo "  - Course URL:          $COURSE_URL"
echo "  - Enrollment URL:      $ENROLLMENT_URL"
echo "=========================================================="

success_count=0
fail_count=0

for ((i=1; i<=TOTAL_REQUESTS; i++)); do
    echo -n "[$i/$TOTAL_REQUESTS] "
    case $MODE in
        health)
            # Peticiones periódicas a health y metrics
            status_code=$(curl -s -o /dev/null -w "%{http_code}" "$COURSE_URL/health")
            echo -n "Course /health: $status_code | "
            status_code_b=$(curl -s -o /dev/null -w "%{http_code}" "$ENROLLMENT_URL/health")
            echo "Enrollment /health: $status_code_b"
            ;;
        normal)
            # Mezcla de consulta de cursos y matrículas estándar
            if (( i % 2 == 0 )); then
                # Consultar cursos
                status_code=$(curl -s -o /dev/null -w "%{http_code}" "$COURSE_URL/api/courses")
                echo "GET /api/courses -> HTTP $status_code"
            else
                # Matricular estudiante
                student_id=$((1000 + i))
                json_data="{\"studentName\":\"Estudiante $student_id\",\"studentEmail\":\"estudiante$student_id@vallegrande.edu.pe\",\"courseId\":\"CRS-001\"}"
                status_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ENROLLMENT_URL/api/enrollments" \
                    -H "Content-Type: application/json" -d "$json_data")
                echo "POST /api/enrollments -> HTTP $status_code"
            fi
            ;;
        timeout)
            # Tráfico intenso hacia enrollment cuando course-service tiene delay
            student_id=$((2000 + i))
            json_data="{\"studentName\":\"Alumno Lento $student_id\",\"studentEmail\":\"lento$student_id@vallegrande.edu.pe\",\"courseId\":\"CRS-002\"}"
            start_time=$(date +%s%N)
            status_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ENROLLMENT_URL/api/enrollments" \
                -H "Content-Type: application/json" -d "$json_data" || echo "000")
            end_time=$(date +%s%N)
            duration_ms=$(( (end_time - start_time) / 1000000 ))
            echo "POST /api/enrollments -> HTTP $status_code (duración: ${duration_ms} ms)"
            ;;
        exhaust)
            # Intentar matricular repetidamente para agotar cupos (Casuística 3)
            student_id=$((3000 + i))
            json_data="{\"studentName\":\"Alumno Racha $student_id\",\"studentEmail\":\"racha$student_id@vallegrande.edu.pe\",\"courseId\":\"CRS-004\"}"
            status_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ENROLLMENT_URL/api/enrollments" \
                -H "Content-Type: application/json" -d "$json_data")
            echo "POST /api/enrollments (Curso CRS-004) -> HTTP $status_code"
            ;;
    esac

    if [[ "$status_code" =~ ^2 ]]; then
        success_count=$((success_count + 1))
    else
        fail_count=$((fail_count + 1))
    fi

    sleep "$SLEEP_INTERVAL"
done

echo "=========================================================="
echo "Generación de tráfico finalizada."
echo "  - Exitosas (HTTP 2xx): $success_count"
echo "  - Fallidas/Timeouts:   $fail_count"
echo "=========================================================="
