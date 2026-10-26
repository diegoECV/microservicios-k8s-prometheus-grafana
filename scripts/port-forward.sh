#!/usr/bin/env bash
# ==============================================================================
# Script para reenviar puertos (Port-Forward) en Linux / Minikube con Podman
# Valle Grande - Observabilidad de Microservicios
# ==============================================================================

echo "Iniciando reenvío persistente de puertos a localhost..."

# Matar procesos de port-forward previos si existen
pkill -f "kubectl port-forward" 2>/dev/null || true
sleep 1

# 1. Grafana -> localhost:3000
setsid kubectl port-forward svc/prometheus-stack-grafana -n monitoring 3000:80 </dev/null >/tmp/pf-grafana.log 2>&1 &
disown

# 2. Prometheus -> localhost:9090
setsid kubectl port-forward svc/prometheus-stack-kube-prom-prometheus -n monitoring 9090:9090 </dev/null >/tmp/pf-prom.log 2>&1 &
disown

# 3. Course Service -> localhost:8081
setsid kubectl port-forward svc/course-service -n valle-grande 8081:8081 </dev/null >/tmp/pf-course.log 2>&1 &
disown

# 4. Enrollment Service -> localhost:8082
setsid kubectl port-forward svc/enrollment-service -n valle-grande 8082:8082 </dev/null >/tmp/pf-enroll.log 2>&1 &
disown

sleep 2

echo "=========================================================="
echo "✔ Todos los puertos han sido redirigidos a localhost:"
echo "  📊 Grafana:            http://localhost:3000 (admin / AdminPassword2026!)"
echo "  🔥 Prometheus:         http://localhost:9090"
echo "  🎓 Course Service:     http://localhost:8081/api/courses"
echo "  📝 Enrollment Service: http://localhost:8082/api/enrollments"
echo "=========================================================="
