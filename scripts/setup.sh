#!/usr/bin/env bash
# ==============================================================================
# Setup Automatizado de Infraestructura, Microservicios y Monitoreo
# Valle Grande - Monitoreo Kubernetes (Prometheus & Grafana)
# ==============================================================================

set -euo pipefail

# Colores de salida
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo -e "${BLUE}==================================================================${NC}"
echo -e "${BLUE}      VALLE GRANDE - SETUP COMPLETO DE ENTORNO KUBERNETES         ${NC}"
echo -e "${BLUE}==================================================================${NC}"

# 1. Verificación de Prerrequisitos
echo -e "\n${YELLOW}[Paso 1/7] Verificando herramientas instaladas...${NC}"
for tool in minikube kubectl helm; do
    if ! command -v "$tool" &>/dev/null; then
        echo -e "${RED}ERROR: La herramienta '$tool' no está instalada o no se encuentra en el PATH.${NC}"
        exit 1
    fi
    echo -e "  ✔ $tool detectado: $("$tool" version --short 2>/dev/null || "$tool" version 2>/dev/null | head -n 1)"
done

# Verificar docker o podman
if command -v docker &>/dev/null; then
    echo -e "  ✔ docker CLI detectado"
elif command -v podman &>/dev/null; then
    echo -e "  ✔ podman CLI detectado"
else
    echo -e "${RED}ERROR: No se encontró ni docker ni podman instalado.${NC}"
    exit 1
fi

# 2. Iniciar Minikube si no está corriendo
echo -e "\n${YELLOW}[Paso 2/7] Verificando estado del clúster Minikube...${NC}"
if ! minikube status | grep -q "Running"; then
    echo -e "  Iniciando Minikube con 4 CPUs y 6GB RAM..."
    minikube start --cpus=4 --memory=6144
else
    echo -e "  ✔ Clúster Minikube ya se encuentra en ejecución."
fi

# 3. Habilitar addon metrics-server
echo -e "\n${YELLOW}[Paso 3/7] Habilitando addon metrics-server en Minikube...${NC}"
minikube addons enable metrics-server

# 4. Instalación del Stack de Monitoreo con Helm (kube-prometheus-stack)
echo -e "\n${YELLOW}[Paso 4/7] Desplegando stack Prometheus + Grafana con Helm...${NC}"
echo "  Agregando repositorio prometheus-community..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update >/dev/null 2>&1
helm repo update >/dev/null 2>&1

# Crear namespaces
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "${ROOT_DIR}/k8s/namespace.yaml"

echo "  Instalando/Actualizando kube-prometheus-stack..."
helm upgrade --install prometheus-stack prometheus-community/kube-prometheus-stack \
    --namespace monitoring \
    -f "${ROOT_DIR}/k8s/monitoring-values.yaml" \
    --wait --timeout 10m

# 5. Compilación y Construcción de Imágenes Docker en Minikube
echo -e "\n${YELLOW}[Paso 5/7] Construyendo imágenes de microservicios en el daemon de Minikube...${NC}"

# Método 1: Intentar minikube image build (nativo y funciona con cualquier driver)
# Método 2: Fallback a eval $(minikube docker-env) && docker build
if minikube image build -t course-service:1.0.0 "${ROOT_DIR}/microservices/course-service" 2>/dev/null; then
    echo -e "  ✔ Imagen course-service:1.0.0 construida exitosamente en Minikube."
else
    echo -e "  Usando entorno docker de Minikube..."
    eval "$(minikube docker-env 2>/dev/null || minikube podman-env 2>/dev/null)"
    docker build -t course-service:1.0.0 "${ROOT_DIR}/microservices/course-service"
fi

if minikube image build -t enrollment-service:1.0.0 "${ROOT_DIR}/microservices/enrollment-service" 2>/dev/null; then
    echo -e "  ✔ Imagen enrollment-service:1.0.0 construida exitosamente en Minikube."
else
    eval "$(minikube docker-env 2>/dev/null || minikube podman-env 2>/dev/null)"
    docker build -t enrollment-service:1.0.0 "${ROOT_DIR}/microservices/enrollment-service"
fi

# 6. Despliegue de Manifiestos de Kubernetes
echo -e "\n${YELLOW}[Paso 6/7] Aplicando manifiestos de Kubernetes para los microservicios...${NC}"
kubectl apply -f "${ROOT_DIR}/k8s/course-service/"
kubectl apply -f "${ROOT_DIR}/k8s/enrollment-service/"
kubectl apply -f "${ROOT_DIR}/k8s/prometheus-rules.yaml"

echo "  Esperando a que los microservicios estén en estado Ready..."
kubectl wait --namespace valle-grande --for=condition=ready pod --selector=app=course-service --timeout=180s
kubectl wait --namespace valle-grande --for=condition=ready pod --selector=app=enrollment-service --timeout=180s

# 7. Información de Acceso y URLs
"${ROOT_DIR}/scripts/port-forward.sh"

echo -e "\n${GREEN}==================================================================${NC}"
echo -e "${GREEN}      ¡DESPLIEGUE COMPLETADO EXITOSAMENTE CON MONITOREO!         ${NC}"
echo -e "${GREEN}==================================================================${NC}"
echo ""
echo -e "Servicios Disponibles en tu Navegador (Localhost):"
echo -e "  📊 Grafana:            ${BLUE}http://localhost:3000${NC} (Usuario: ${YELLOW}admin${NC} / Clave: ${YELLOW}AdminPassword2026!${NC})"
echo -e "  🔥 Prometheus:         ${BLUE}http://localhost:9090${NC}"
echo -e "  🎓 Course Service (A): ${BLUE}http://localhost:8081/api/courses${NC} | Metrics: ${BLUE}http://localhost:8081/metrics${NC}"
echo -e "  📝 Enrollment Svc (B): ${BLUE}http://localhost:8082/api/enrollments${NC} | Metrics: ${BLUE}http://localhost:8082/metrics${NC}"
echo ""
echo -e "${YELLOW}Para importar el dashboard en Grafana:${NC}"
echo -e "  Ingresa a Grafana -> Dashboards -> Import -> Carga el archivo ${BLUE}docs/grafana-dashboard.json${NC}"
echo -e "=================================================================="
